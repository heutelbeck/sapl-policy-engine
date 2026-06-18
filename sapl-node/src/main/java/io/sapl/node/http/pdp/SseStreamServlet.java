/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.http.pdp;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.sapl.api.stream.Stream;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthenticationException;
import io.sapl.node.http.RequestBodyTooLargeException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for the {@code text/event-stream} bypass-Spring servlets. Each
 * concrete subclass parses the request body into a typed subscription,
 * opens a SAPL {@link Stream} of decisions, and lets this base class pump
 * decisions out as Server-Sent Events on a virtual thread, with optional
 * keep-alive comment frames.
 *
 * @param <S> the subscription type
 * @param <D> the decision type emitted by the stream
 */
@Slf4j
public abstract class SseStreamServlet<S, D> extends AbstractBypassServlet {

    private static final String CONTENT_TYPE_SSE = "text/event-stream;charset=UTF-8";
    private static final String KEEP_ALIVE_FRAME = ": keep-alive\n\n";

    // Keep-alive is tunable but not disableable. A non-positive interval falls back
    // to the default, a positive one is floored.
    private static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(15);
    private static final Duration MIN_KEEP_ALIVE     = Duration.ofSeconds(1);

    private final HttpAuthHandler          authHandler;
    private final JsonMapper               mapper;
    private final Duration                 keepAliveInterval;
    private final ScheduledExecutorService keepAliveScheduler;
    private final ExecutorService          pumpExecutor;
    private final SseConnectionRegistry    connectionRegistry;

    protected SseStreamServlet(HttpAuthHandler authHandler,
            JsonMapper mapper,
            Duration keepAliveInterval,
            ScheduledExecutorService keepAliveScheduler,
            ExecutorService pumpExecutor,
            SseConnectionRegistry connectionRegistry) {
        this.authHandler        = authHandler;
        this.mapper             = mapper;
        this.keepAliveInterval  = effectiveKeepAliveInterval(keepAliveInterval);
        this.keepAliveScheduler = keepAliveScheduler;
        this.pumpExecutor       = pumpExecutor;
        this.connectionRegistry = connectionRegistry;
    }

    /**
     * Normalizes the configured keep-alive interval. Keep-alive cannot be
     * disabled: a {@code null}, zero, or negative interval falls back to the
     * default, and an interval below the minimum is raised to it.
     *
     * @param configured the configured interval, may be null
     * @return a positive, floored interval
     */
    static Duration effectiveKeepAliveInterval(@Nullable Duration configured) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return DEFAULT_KEEP_ALIVE;
        }
        return configured.compareTo(MIN_KEEP_ALIVE) < 0 ? MIN_KEEP_ALIVE : configured;
    }

    /**
     * The runtime class of the subscription type for Jackson.
     *
     * @return the subscription class
     */
    protected abstract Class<S> subscriptionType();

    /**
     * Opens the PDP stream for the given subscription and tenant.
     *
     * @param subscription the parsed subscription
     * @param pdpId the resolved tenant identifier
     * @return the SAPL stream
     */
    protected abstract Stream<D> openStream(S subscription, String pdpId);

    /**
     * Builds an indeterminate / fallback decision for upstream failures.
     *
     * @return the fallback decision value
     */
    protected abstract D indeterminate();

    @Override
    protected void handlePost(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response)
            throws ServletException, IOException {
        String pdpId;
        try {
            pdpId = authHandler.authenticate(request).pdpId();
        } catch (HttpAuthenticationException e) {
            log.debug("HTTP authentication failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed.");
            return;
        }

        S subscription;
        try (val in = request.getInputStream()) {
            subscription = mapper.readValue(in, subscriptionType());
        } catch (IOException | JacksonException e) {
            if (RequestBodyTooLargeException.isCausedBy(e)) {
                log.debug("Rejected oversized subscription: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Request body exceeds the configured limit.");
                return;
            }
            log.debug("Failed to parse subscription: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed subscription.");
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE_SSE);
        response.setHeader("Cache-Control", "no-cache");
        response.flushBuffer();

        val asyncContext = request.startAsync();
        asyncContext.setTimeout(0);
        // The writer lock serializes every write to the response: the pump, the
        // keep-alive frame, and the registry's shutdown frame. Sharing it with the
        // registry prevents the shutdown write from interleaving with a pump or
        // keep-alive write on the same non-thread-safe PrintWriter.
        val writerLock = new Object();
        connectionRegistry.register(asyncContext, writerLock);

        try {
            pumpExecutor.submit(() -> pump(asyncContext, subscription, pdpId, writerLock));
        } catch (RejectedExecutionException e) {
            log.warn("SSE pump rejected by executor for pdpId={}: {}", pdpId, e.getMessage());
            connectionRegistry.unregister(asyncContext);
            asyncContext.complete();
        }
    }

    /**
     * Drives the SSE response from a SAPL {@link Stream} of decisions.
     * <p>
     * Backpressure is structural, not explicit: {@code openStream} returns
     * a {@code LatestSlotStream} whose producer overwrites the single slot
     * when the consumer lags. {@code awaitNext} therefore reads only the
     * most recent decision, and a slow client throttles the pump by
     * blocking on socket flush via TCP flow control. Old decisions are
     * dropped at the source slot rather than queued; the response buffer
     * is bounded by the container's configured response buffer plus the
     * kernel socket send buffer.
     */
    private void pump(AsyncContext asyncContext, S subscription, String pdpId, Object writerLock) {
        val                response      = (HttpServletResponse) asyncContext.getResponse();
        ScheduledFuture<?> keepAliveTask = null;
        try (PrintWriter writer = response.getWriter(); Stream<D> stream = openStream(subscription, pdpId)) {
            keepAliveTask = scheduleKeepAlive(writer, writerLock, stream);
            try {
                while (!Thread.currentThread().isInterrupted() && processNextEvent(stream, writer, writerLock, pdpId)) {
                    // loop body intentionally empty; processNextEvent drives one iteration
                }
            } catch (Exception e) {
                log.debug("SSE stream terminated for pdpId={}: {}", pdpId, e.getMessage());
                if (keepAliveTask != null) {
                    keepAliveTask.cancel(false);
                    keepAliveTask = null;
                }
                if (!writer.checkError()) {
                    writeEvent(writer, indeterminate(), writerLock);
                }
            }
        } catch (Exception e) {
            log.debug("SSE stream setup failed for pdpId={}: {}", pdpId, e.getMessage());
        } finally {
            if (keepAliveTask != null) {
                keepAliveTask.cancel(false);
            }
            connectionRegistry.unregister(asyncContext);
            asyncContext.complete();
        }
    }

    /**
     * One iteration of the pump loop. Returns true when the loop should
     * continue, false when the stream has ended, the client has disconnected,
     * the pump was interrupted, or serialisation failed irrecoverably.
     */
    private boolean processNextEvent(Stream<D> stream, PrintWriter writer, Object writerLock, String pdpId) {
        D decision;
        try {
            decision = stream.awaitNext();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (decision == null) {
            return false;
        }
        if (!writeEvent(writer, decision, writerLock)) {
            return false;
        }
        if (writer.checkError()) {
            log.debug("SSE stream client disconnected for pdpId={}", pdpId);
            return false;
        }
        return true;
    }

    @Nullable
    private ScheduledFuture<?> scheduleKeepAlive(PrintWriter writer, Object writerLock, Stream<D> stream) {
        if (keepAliveScheduler == null) {
            return null;
        }
        val periodMillis = keepAliveInterval.toMillis();
        // The scheduler thread only dispatches; the blocking write+flush runs on the
        // per-connection virtual-thread pump. A slow client that stalls the flush via
        // TCP flow control therefore parks a cheap virtual thread, never a thread of
        // the bounded keep-alive scheduler pool.
        return keepAliveScheduler.scheduleAtFixedRate(() -> dispatchKeepAlive(writer, writerLock, stream), periodMillis,
                periodMillis, TimeUnit.MILLISECONDS);
    }

    private void dispatchKeepAlive(PrintWriter writer, Object writerLock, Stream<D> stream) {
        try {
            pumpExecutor.execute(() -> sendKeepAlive(writer, writerLock, stream));
        } catch (RejectedExecutionException e) {
            log.debug("Keep-alive dispatch rejected; pump executor is shutting down: {}", e.getMessage());
        }
    }

    private void sendKeepAlive(PrintWriter writer, Object writerLock, Stream<D> stream) {
        boolean clientGone;
        synchronized (writerLock) {
            writer.write(KEEP_ALIVE_FRAME);
            writer.flush();
            // PrintWriter swallows a broken-pipe write but sets the error flag, the only
            // signal of a dead idle client.
            clientGone = writer.checkError();
        }
        if (clientGone) {
            // Unblocks the pump parked in awaitNext, whose finally block then tears down
            // this task.
            closeQuietly(stream);
        }
    }

    private void closeQuietly(Stream<D> stream) {
        try {
            stream.close();
        } catch (Exception e) {
            log.debug("Closing SSE stream after client disconnect failed: {}", e.getMessage());
        }
    }

    /**
     * Returns true on success, false on persistent failure (Jackson serialization
     * error). The pump uses the return value to break out of the loop instead of
     * re-attempting to write subsequent events to a doomed stream.
     */
    private boolean writeEvent(PrintWriter writer, D value, Object writerLock) {
        try {
            val json = mapper.writeValueAsString(value);
            synchronized (writerLock) {
                writer.write("data:");
                writer.write(json);
                writer.write("\n\n");
                writer.flush();
            }
            return true;
        } catch (JacksonException e) {
            log.debug("Failed to serialize SSE event: {}", e.getMessage());
            return false;
        }
    }
}
