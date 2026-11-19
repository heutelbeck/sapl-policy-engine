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
 */
@Slf4j
final class RSocketPool {

    private static final String ERROR_REOPEN_FAILED = "A connection of the pool to the PDP cannot be opened ({}), it is tried again after a backoff.";

    private final Mono<RSocket> connect;
    private final Slot[]        slots;
    private final int           firstBackoffMillis;
    private final int           maxBackOffMillis;
    private final AtomicBoolean disposed = new AtomicBoolean();
    // Where the search for the connection with the fewest requests in flight
    // starts, so that connections with as many take turns.
    private final AtomicInteger turn = new AtomicInteger();

    /**
     * @param connect opens a new connection at each subscription, within the
     * timeout of the client
     * @param connections how many connections the pool holds, at least one
     * @param firstBackoffMillis the first wait before a closed connection is
     * opened anew after a failure
     * @param maxBackOffMillis the longest wait
     */
    RSocketPool(Mono<RSocket> connect, int connections, int firstBackoffMillis, int maxBackOffMillis) {
        this.connect            = connect;
        this.slots              = IntStream.range(0, connections).mapToObj(index -> new Slot()).toArray(Slot[]::new);
        this.firstBackoffMillis = firstBackoffMillis;
        this.maxBackOffMillis   = maxBackOffMillis;
    }

    /**
     * Sends a one-shot request on the open connection with the fewest requests
     * in flight. It counts as in flight on it until it ends.
     */
    <T> Mono<T> request(Function<RSocket, Mono<T>> send) {
        return Mono.usingWhen(lease(), lease -> send.apply(lease.socket()), Lease::release);
    }

    /**
     * Opens a stream on the open connection with the fewest requests in flight.
     * It stays on that connection and counts as in flight on it until it ends.
     */
    <T> Flux<T> stream(Function<RSocket, Flux<T>> open) {
        return Flux.usingWhen(lease(), lease -> open.apply(lease.socket()), Lease::release);
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
            return open(first).doOnNext(socket -> reopenClosed()).map(socket -> Lease.on(first, socket));
        });
    }

    private @Nullable Lease leastBusy() {
        val     start       = Math.floorMod(turn.getAndIncrement(), slots.length);
        Slot    best        = null;
        RSocket bestSocket  = null;
        var     fewest      = Integer.MAX_VALUE;
        var     foundClosed = false;
        for (var offset = 0; offset < slots.length; offset++) {
            val slot     = slots[(start + offset) % slots.length];
            val inFlight = slot.inFlight.get();
            if (best != null && fewest <= inFlight) {
                continue;
            }
            val socket = slot.liveSocket();
            if (socket == null) {
                foundClosed = true;
            } else {
                best       = slot;
                bestSocket = socket;
                fewest     = inFlight;
            }
        }
        if (best == null) {
            return null;
        }
        if (foundClosed) {
            reopenClosed();
        }
        return Lease.on(best, bestSocket);
    }

    // The open connection of the slot, or a new one. Requests that find none at
    // the same time share one attempt instead of each opening a socket.
    private Mono<RSocket> open(Slot slot) {
        return Mono.defer(() -> {
            val live = slot.liveSocket();
            if (live != null) {
                return Mono.just(live);
            }
            return slot.connecting.updateAndGet(current -> current != null ? current
                    : connect.doOnNext(socket -> slot.keepUnlessDisposed(socket, disposed.get()))
                            .doFinally(signal -> slot.connecting.set(null)).cache());
        });
    }

    // Opens each closed connection anew in the background, after a backoff after
    // each failure, unless the pool is disposed or another attempt is under way.
    private void reopenClosed() {
        for (val slot : slots) {
            if (!disposed.get() && slot.liveSocket() == null && slot.reopening.compareAndSet(false, true)) {
                val failures = new AtomicLong();
                val reopen   = open(slot).retryWhen(Retry.from(signals -> signals.concatMap(signal -> {
                                 if (disposed.get()) {
                                     return Mono.error(signal.failure());
                                 }
                                 log.debug(ERROR_REOPEN_FAILED, signal.failure().getMessage());
                                 return Mono.delay(RemotePdpRetry.backoffWithJitter(failures.incrementAndGet(),
                                         firstBackoffMillis, maxBackOffMillis));
                             })))
                        .doFinally(signal -> slot.reopening.set(false)).subscribe(socket -> {}, error -> {});
                slot.replaceReopener(reopen);
            }
        }
    }

    /**
     * The place of one connection in the pool.
     */
    private static final class Slot {

        private final AtomicReference<@Nullable RSocket>       socket     = new AtomicReference<>();
        private final AtomicReference<@Nullable Mono<RSocket>> connecting = new AtomicReference<>();
        private final AtomicReference<@Nullable Disposable>    reopener   = new AtomicReference<>();
        private final AtomicBoolean                            reopening  = new AtomicBoolean();
        private final AtomicInteger                            inFlight   = new AtomicInteger();

        @Nullable
        RSocket liveSocket() {
            val current = socket.get();
            return current != null && !current.isDisposed() ? current : null;
        }

        // A socket that arrives after the pool is disposed is disposed here, so that
        // an attempt under way cannot leak a live connection past shutdown.
        void keepUnlessDisposed(RSocket opened, boolean poolDisposed) {
            if (poolDisposed) {
                opened.dispose();
            } else {
                socket.set(opened);
            }
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
            val current = socket.getAndSet(null);
            if (current != null) {
                current.dispose();
            }
        }
    }

    /**
     * A connection with a request in flight on it, until it is released.
     */
    private record Lease(Slot slot, RSocket socket) {

        static Lease on(Slot slot, RSocket socket) {
            slot.inFlight.incrementAndGet();
            return new Lease(slot, socket);
        }

        Mono<Void> release() {
            return Mono.fromRunnable(slot.inFlight::decrementAndGet);
        }
    }
}
