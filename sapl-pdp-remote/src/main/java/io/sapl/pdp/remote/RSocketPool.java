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
package io.sapl.pdp.remote;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import io.rsocket.RSocket;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.exceptions.UnsupportedSetupException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * The RSocket connections of a client to one node, one unless configured.
 * <p>
 * The first connection is opened at the first request, the others in the
 * background once it is open. A request goes to the open connection with the
 * fewest requests in flight, open streams included, and a stream stays on the
 * connection it was opened on. A connection that is closed is opened anew in
 * the background, with the backoff of the streams, while the others serve.
 * When no connection is open, the next request opens the first one anew and
 * waits for it, as the client with a single connection does.
 * <p>
 * A connection in the SAPL binary format falls back to protobuf when the node
 * does not speak the format: it refused the SETUP as unsupported, or refused a
 * request as invalid before the connection answered any, as a node does that
 * reads protobuf whatever the data MIME type. The connection is replaced by one
 * in protobuf and the request is sent again on it. The next connection of its
 * place in the pool tries the binary format again.
 */
@Slf4j
final class RSocketPool {

    private static final String ERROR_REOPEN_FAILED       = "A connection of the pool to the PDP cannot be opened ({}), it is tried again after a backoff.";
    private static final String WARN_FALLBACK_TO_PROTOBUF = "The PDP refused the SAPL binary format on a connection. The connection is opened anew in protobuf.";

    private final Function<PayloadFormat, Mono<RSocket>> connect;
    private final PayloadFormat                          format;
    private final Slot[]                                 slots;
    private final int                                    firstBackoffMillis;
    private final int                                    maxBackOffMillis;
    private final AtomicBoolean                          disposed = new AtomicBoolean();
    // Where the search for the connection with the fewest requests in flight
    // starts, so that connections with as many take turns.
    private final AtomicInteger turn = new AtomicInteger();

    /**
     * @param connect opens a new connection in a format at each subscription,
     * within the timeout of the client
     * @param format the format the client asks for
     * @param connections how many connections the pool holds, at least one
     * @param firstBackoffMillis the first wait before a closed connection is
     * opened anew after a failure
     * @param maxBackOffMillis the longest wait
     */
    RSocketPool(Function<PayloadFormat, Mono<RSocket>> connect,
            PayloadFormat format,
            int connections,
            int firstBackoffMillis,
            int maxBackOffMillis) {
        this.connect            = connect;
        this.format             = format;
        this.slots              = IntStream.range(0, connections).mapToObj(index -> new Slot()).toArray(Slot[]::new);
        this.firstBackoffMillis = firstBackoffMillis;
        this.maxBackOffMillis   = maxBackOffMillis;
    }

    /**
     * Sends a one-shot request on the open connection with the fewest requests
     * in flight. It counts as in flight on it until it ends. If the node refuses
     * the binary format, the request is sent again on a connection in protobuf.
     */
    <T> Mono<T> request(Function<Connection, Mono<T>> send) {
        return Mono.usingWhen(lease(),
                lease -> send.apply(lease.connection()).doOnNext(answer -> lease.connection().answered().set(true))
                        .onErrorResume(lease.connection()::refusesFormat,
                                refusal -> Mono.usingWhen(fallBack(lease),
                                        fallback -> send.apply(fallback.connection()), Lease::release)),
                Lease::release);
    }

    /**
     * Opens a stream on the open connection with the fewest requests in flight.
     * It stays on that connection and counts as in flight on it until it ends.
     * If the node refuses the binary format, the stream is opened again on a
     * connection in protobuf.
     */
    <T> Flux<T> stream(Function<Connection, Flux<T>> open) {
        return Flux.usingWhen(lease(),
                lease -> open.apply(lease.connection()).doOnNext(answer -> lease.connection().answered().set(true))
                        .onErrorResume(lease.connection()::refusesFormat,
                                refusal -> Flux.usingWhen(fallBack(lease),
                                        fallback -> open.apply(fallback.connection()), Lease::release)),
                Lease::release);
    }

    /**
     * Closes every connection and stops opening new ones.
     */
    void dispose() {
        disposed.set(true);
        for (val slot : slots) {
            slot.dispose();
        }
    }

    private Mono<Lease> lease() {
        return Mono.defer(() -> {
            val leastBusy = leastBusy();
            if (leastBusy != null) {
                return Mono.just(leastBusy);
            }
            val first = slots[0];
            return open(first).doOnNext(connection -> reopenClosed()).map(connection -> Lease.on(first, connection));
        });
    }

    // Replaces the connection of a lease, on which the node refused the binary
    // format, with one in protobuf, unless another request has done so meanwhile.
    private Mono<Lease> fallBack(Lease refused) {
        return Mono.defer(() -> {
            val slot    = refused.slot();
            val current = slot.liveConnection();
            if (current != null && current.format() == PayloadFormat.PROTOBUF) {
                return Mono.just(Lease.on(slot, current));
            }
            slot.forget(refused.connection());
            slot.fallsBack.set(true);
            return open(slot).map(connection -> Lease.on(slot, connection));
        });
    }

    private @Nullable Lease leastBusy() {
        val        start          = Math.floorMod(turn.getAndIncrement(), slots.length);
        Slot       best           = null;
        Connection bestConnection = null;
        var        fewest         = Integer.MAX_VALUE;
        var        foundClosed    = false;
        for (var offset = 0; offset < slots.length; offset++) {
            val slot     = slots[(start + offset) % slots.length];
            val inFlight = slot.inFlight.get();
            if (best != null && fewest <= inFlight) {
                continue;
            }
            val connection = slot.liveConnection();
            if (connection == null) {
                foundClosed = true;
            } else {
                best           = slot;
                bestConnection = connection;
                fewest         = inFlight;
            }
        }
        if (best == null) {
            return null;
        }
        if (foundClosed) {
            reopenClosed();
        }
        return Lease.on(best, bestConnection);
    }

    // The open connection of the slot, or a new one. Requests that find none at
    // the same time share one attempt instead of each opening a socket. A new
    // connection is in protobuf if the slot falls back, else in the format the
    // client asks for.
    private Mono<Connection> open(Slot slot) {
        return Mono.defer(() -> {
            val live = slot.liveConnection();
            if (live != null) {
                return Mono.just(live);
            }
            return slot.connecting.updateAndGet(current -> current != null ? current : connectFor(slot));
        });
    }

    private Mono<Connection> connectFor(Slot slot) {
        return Mono.defer(() -> {
            val connectionFormat = slot.fallsBack.getAndSet(false) ? PayloadFormat.PROTOBUF : format;
            if (connectionFormat != format) {
                log.warn(WARN_FALLBACK_TO_PROTOBUF);
            }
            return connect.apply(connectionFormat).map(socket -> new Connection(socket, connectionFormat));
        }).doOnNext(connection -> slot.keepUnlessDisposed(connection, disposed.get()))
                .doFinally(signal -> slot.connecting.set(null)).cache();
    }

    // Opens each closed connection anew in the background, after a backoff after
    // each failure, unless the pool is disposed or another attempt is under way.
    private void reopenClosed() {
        for (val slot : slots) {
            if (!disposed.get() && slot.liveConnection() == null && slot.reopening.compareAndSet(false, true)) {
                val failures = new AtomicLong();
                val reopen   = open(slot).retryWhen(Retry.from(signals -> signals.concatMap(signal -> {
                                 if (disposed.get()) {
                                     return Mono.error(signal.failure());
                                 }
                                 log.debug(ERROR_REOPEN_FAILED, signal.failure().getMessage());
                                 return Mono.delay(RemotePdpRetry.backoffWithJitter(failures.incrementAndGet(),
                                         firstBackoffMillis, maxBackOffMillis));
                             })))
                        .doFinally(signal -> slot.reopening.set(false)).subscribe(connection -> {}, error -> {});
                slot.replaceReopener(reopen);
            }
        }
    }

    /**
     * An open connection of the pool, the format its SETUP named, and whether the
     * node has answered a request on it, which shows that it reads the format.
     */
    record Connection(RSocket socket, PayloadFormat format, AtomicBoolean answered) {

        Connection(RSocket socket, PayloadFormat format) {
            this(socket, format, new AtomicBoolean());
        }

        /**
         * Whether an error shows that the node does not speak the binary format of
         * the connection: it refused the SETUP as unsupported, or a request as
         * invalid before it answered any.
         */
        boolean refusesFormat(Throwable error) {
            return format == PayloadFormat.BINARY && (error instanceof UnsupportedSetupException
                    || (error instanceof InvalidException && !answered.get()));
        }
    }

    /**
     * The place of one connection in the pool.
     */
    private static final class Slot {

        private final AtomicReference<@Nullable Connection>       connection = new AtomicReference<>();
        private final AtomicReference<@Nullable Mono<Connection>> connecting = new AtomicReference<>();
        private final AtomicReference<@Nullable Disposable>       reopener   = new AtomicReference<>();
        private final AtomicBoolean                               reopening  = new AtomicBoolean();
        // Whether the next connection falls back to protobuf, as the node refused the
        // binary format at the SETUP of the one before.
        private final AtomicBoolean fallsBack = new AtomicBoolean();
        private final AtomicInteger inFlight  = new AtomicInteger();

        @Nullable
        Connection liveConnection() {
            val current = connection.get();
            return current != null && !current.socket().isDisposed() ? current : null;
        }

        // A connection that arrives after the pool is disposed is disposed here, so that
        // an attempt under way cannot leak a live connection past shutdown. A binary
        // connection whose SETUP the node refuses makes the next one fall back.
        void keepUnlessDisposed(Connection opened, boolean poolDisposed) {
            if (poolDisposed) {
                opened.socket().dispose();
                return;
            }
            connection.set(opened);
            Mono.defer(opened.socket()::onClose).subscribe(closed -> {}, error -> {
                // A connection that was replaced already has fallen back on its own.
                if (opened.refusesFormat(error) && connection.get() == opened) {
                    fallsBack.set(true);
                }
            });
        }

        // Forgets the connection unless another one has taken its place already.
        void forget(Connection ended) {
            connection.compareAndSet(ended, null);
        }

        void replaceReopener(Disposable reopen) {
            val previous = reopener.getAndSet(reopen);
            if (previous != null) {
                previous.dispose();
            }
        }

        void dispose() {
            val reopen = reopener.getAndSet(null);
            if (reopen != null) {
                reopen.dispose();
            }
            connecting.set(null);
            val current = connection.getAndSet(null);
            if (current != null) {
                current.socket().dispose();
            }
        }
    }

    /**
     * A connection with a request in flight on it, until it is released.
     */
    private record Lease(Slot slot, Connection connection) {

        static Lease on(Slot slot, Connection connection) {
            slot.inFlight.incrementAndGet();
            return new Lease(slot, connection);
        }

        Mono<Void> release() {
            return Mono.fromRunnable(slot.inFlight::decrementAndGet);
        }
    }
}
