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
package io.sapl.node.http.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.sapl.api.SaplVersion;
import io.sapl.api.stream.Stream;
import io.sapl.node.http.auth.HttpAuthHandler;
import io.sapl.node.http.auth.HttpAuthenticationException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
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
public abstract class SseStreamServlet<S, D> extends HttpServlet {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String CONTENT_TYPE_SSE = "text/event-stream;charset=UTF-8";
    private static final String KEEP_ALIVE_FRAME = ": keep-alive\n\n";

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
        this.keepAliveInterval  = keepAliveInterval;
        this.keepAliveScheduler = keepAliveScheduler;
        this.pumpExecutor       = pumpExecutor;
        this.connectionRegistry = connectionRegistry;
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
    protected void doPost(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response)
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
        connectionRegistry.register(asyncContext);

        try {
            pumpExecutor.submit(() -> pump(asyncContext, subscription, pdpId));
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
    private void pump(AsyncContext asyncContext, S subscription, String pdpId) {
        val                response      = (HttpServletResponse) asyncContext.getResponse();
        ScheduledFuture<?> keepAliveTask = null;
        try (PrintWriter writer = response.getWriter(); Stream<D> stream = openStream(subscription, pdpId)) {
            keepAliveTask = scheduleKeepAlive(writer);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    D decision;
                    try {
                        decision = stream.awaitNext();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (decision == null) {
                        return;
                    }
                    if (!writeEvent(writer, decision)) {
                        return;
                    }
                    if (writer.checkError()) {
                        log.debug("SSE stream client disconnected for pdpId={}", pdpId);
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("SSE stream terminated for pdpId={}: {}", pdpId, e.getMessage());
                if (keepAliveTask != null) {
                    keepAliveTask.cancel(false);
                    keepAliveTask = null;
                }
                if (!writer.checkError()) {
                    writeEvent(writer, indeterminate());
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

    @Nullable
    private ScheduledFuture<?> scheduleKeepAlive(PrintWriter writer) {
        if (keepAliveInterval.isZero() || keepAliveInterval.isNegative() || keepAliveScheduler == null) {
            return null;
        }
        val periodMillis = keepAliveInterval.toMillis();
        return keepAliveScheduler.scheduleAtFixedRate(() -> {
            synchronized (writer) {
                writer.write(KEEP_ALIVE_FRAME);
                writer.flush();
            }
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns true on success, false on persistent failure (Jackson serialization
     * error). The pump uses the return value to break out of the loop instead of
     * re-attempting to write subsequent events to a doomed stream.
     */
    private boolean writeEvent(PrintWriter writer, D value) {
        try {
            val json = mapper.writeValueAsString(value);
            synchronized (writer) {
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
