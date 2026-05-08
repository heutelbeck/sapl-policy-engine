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
package io.sapl.api.stream;

import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Factories for building {@link Stream}{@code <Value>} instances.
 * Helpers fall in two groups: pure constructors that emit a finite
 * sequence and complete, and producer-side wrappers that drive a
 * {@link LatestSlotStream} from a virtual-thread loop or an
 * external callback.
 *
 * <h3>Lifecycle contract</h3>
 * <p>
 * Helpers that drive a virtual-thread loop ({@link #concat},
 * {@link #poll}, {@link #fromBlockingSource}, {@link #map}) are
 * <strong>hot</strong>: the producer thread starts at the moment the
 * stream is constructed, before any consumer call. Callers
 * <strong>must</strong> {@link Stream#close()} the returned stream
 * when finished, otherwise the producer thread is leaked.
 * <p>
 * In SAPL the {@code AttributeStore} owns each stream and closes
 * it when the consuming subscription releases. Helpers used outside
 * that path (tests, ad-hoc code) need explicit try-with-resources or
 * a manual {@code close()}.
 *
 * <h3>Threading</h3>
 * <p>
 * Blocking operations inside these streams (HTTP polling, MQTT
 * receive, {@link Stream#awaitNext()}) <strong>must not</strong>
 * run on a Reactor scheduler thread or a Netty event loop. SAPL
 * consumers run on dedicated virtual threads; the helpers themselves
 * also spawn virtual threads. Consumers that bridge from Reactor /
 * WebFlux are expected to spawn or hop onto a virtual thread before
 * calling {@code awaitNext()}.
 */
@UtilityClass
public class Streams {

    /**
     * Stream that emits {@code v} once and completes.
     */
    public static Stream<Value> just(Value v) {
        val s = new LatestSlotStream<Value>();
        s.put(v);
        s.complete();
        return s;
    }

    /**
     * Stream that emits a single error value carrying {@code message}
     * and completes.
     */
    public static Stream<Value> error(String message) {
        return just(Value.error(message));
    }

    /**
     * Empty stream: completes immediately, never emits.
     */
    public static Stream<Value> empty() {
        val s = new LatestSlotStream<Value>();
        s.complete();
        return s;
    }

    /**
     * Stream that emits {@code v} once at {@code when} and then
     * completes. If {@code when} is in the past, the value may be
     * delivered immediately.
     */
    public static Stream<Value> scheduledAt(Value v, Instant when, TimeScheduler scheduler) {
        val s      = new LatestSlotStream<Value>();
        val cancel = scheduler.scheduleAt(when, () -> {
                       s.put(v);
                       s.complete();
                   });
        s.onClose(cancel::cancel);
        return s;
    }

    /**
     * Sequentially emits values from each given stream, in order. The
     * resulting stream completes when every source has completed.
     * Closing the resulting stream closes the currently active source
     * and prevents further sources from being consumed.
     * <p>
     * Hot: spawns one virtual thread that pumps each source in turn.
     * Caller must {@code close()} the returned stream to release the
     * pump thread, otherwise it is leaked.
     */
    @SafeVarargs
    public static Stream<Value> concat(Stream<Value>... sources) {
        val out     = new LatestSlotStream<Value>();
        val stopped = new AtomicBoolean(false);
        val pump    = Thread.startVirtualThread(() -> {
                        try {
                            for (val source : sources) {
                                if (stopped.get()) {
                                    break;
                                }
                                pumpInto(source, out, stopped);
                            }
                        } catch (RuntimeException loopFailure) {
                            out.put(Value.error(messageOf(loopFailure)));
                        } finally {
                            out.complete();
                        }
                    });
        out.onClose(() -> {
            stopped.set(true);
            pump.interrupt();
            for (val source : sources) {
                source.close();
            }
        });
        return out;
    }

    /**
     * Stream that emits {@code source.get()} immediately, then again
     * every {@code interval} until closed. If {@code source} throws,
     * the exception's message is emitted as an error value and the
     * stream continues at the next interval. {@code interval} of zero
     * or negative emits as fast as possible.
     * <p>
     * Hot: spawns one virtual thread that calls {@code source} on a
     * loop. Caller must {@code close()} the returned stream to stop
     * the loop, otherwise the thread polls forever.
     */
    public static Stream<Value> poll(Duration interval, Supplier<Value> source) {
        val s       = new LatestSlotStream<Value>();
        val stopped = new AtomicBoolean(false);
        val thread  = Thread.startVirtualThread(() -> {
                        try {
                            while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
                                emitOnce(s, source);
                                val sleepMillis = interval.toMillis();
                                if (sleepMillis > 0L) {
                                    Thread.sleep(sleepMillis);
                                }
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } finally {
                            s.complete();
                        }
                    });
        s.onClose(() -> {
            stopped.set(true);
            thread.interrupt();
        });
        return s;
    }

    /**
     * Stream that calls {@code next} in a loop on a virtual thread,
     * emitting each returned value. The loop terminates and the
     * stream completes when {@code next} returns {@code null}, when
     * the stream is closed, or when {@code next} throws.
     * <p>
     * Hot: spawns one virtual thread that calls {@code next} on a
     * loop. Caller must {@code close()} the returned stream to
     * interrupt the call, otherwise the thread blocks forever on
     * the next invocation.
     */
    public static Stream<Value> fromBlockingSource(Callable<Value> next) {
        val s       = new LatestSlotStream<Value>();
        val stopped = new AtomicBoolean(false);
        val thread  = Thread.startVirtualThread(() -> {
                        try {
                            while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
                                val v = next.call();
                                if (v == null) {
                                    break;
                                }
                                s.put(v);
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            s.put(Value.error(messageOf(e)));
                        } finally {
                            s.complete();
                        }
                    });
        s.onClose(() -> {
            stopped.set(true);
            thread.interrupt();
        });
        return s;
    }

    /**
     * Bridges a callback-driven producer into a stream. The producer
     * is invoked once with two consumers: {@code emit} pushes a
     * value into the stream and {@code complete} signals end of
     * stream. The producer returns a cleanup {@link Runnable} that
     * the stream invokes on close.
     * <p>
     * This helper does not spawn a virtual thread itself, but the
     * producer typically registers async work (a WebSocket listener,
     * an MQTT subscription, a callback on a third-party SDK) that
     * persists. The cleanup {@link Runnable} returned by the
     * producer must release that work; caller must {@code close()}
     * the returned stream to invoke it.
     */
    public static Stream<Value> fromCallback(CallbackProducer producer) {
        val s       = new LatestSlotStream<Value>();
        val cleanup = producer.produce(s::put, s::complete);
        s.onClose(cleanup);
        return s;
    }

    /**
     * Stream of values transformed by {@code mapper}. The mapper is
     * applied on a virtual thread that pulls from {@code source};
     * each transformed value is pushed into the latest-wins output
     * slot. The output stream completes when {@code source} completes
     * and is closed when either side closes. If {@code mapper}
     * throws, the exception's message is emitted as an error value
     * and the loop terminates.
     * <p>
     * Hot: spawns one virtual thread that pulls from {@code source}.
     * Caller must {@code close()} the returned stream, which also
     * closes {@code source}; closing {@code source} directly is
     * also propagated and stops the pump.
     */
    public static Stream<Value> map(Stream<Value> source, UnaryOperator<Value> mapper) {
        val out     = new LatestSlotStream<Value>();
        val stopped = new AtomicBoolean(false);
        val pump    = Thread.startVirtualThread(() -> {
                        try {
                            while (!stopped.get()) {
                                val v = source.awaitNext();
                                if (v == null) {
                                    return;
                                }
                                out.put(mapper.apply(v));
                            }
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        } catch (RuntimeException e) {
                            out.put(Value.error(messageOf(e)));
                        } finally {
                            out.complete();
                            source.close();
                        }
                    });
        out.onClose(() -> {
            stopped.set(true);
            pump.interrupt();
            source.close();
        });
        return out;
    }

    /**
     * Renders a Throwable as the string we put into an error
     * {@link Value}. Falls back to {@link Throwable#toString()} when
     * the throwable carries no message.
     */
    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    private static void pumpInto(Stream<Value> source, LatestSlotStream<Value> out, AtomicBoolean stopped) {
        try {
            while (!stopped.get()) {
                val v = source.awaitNext();
                if (v == null) {
                    return;
                }
                out.put(v);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            source.close();
        }
    }

    private static void emitOnce(LatestSlotStream<Value> s, Supplier<Value> source) {
        try {
            s.put(source.get());
        } catch (RuntimeException e) {
            s.put(Value.error(messageOf(e)));
        }
    }

    /**
     * Stream that re-creates a fresh source from {@code sourceFactory}
     * each time the previous one completes, forwarding every value
     * into the output. Equivalent of Reactor's {@code .repeat()} for a
     * cold publisher. Runs until the output stream is closed.
     * <p>
     * Hot: spawns one virtual thread that drives the loop. Caller must
     * {@code close()} the returned stream to stop the loop.
     */
    public static Stream<Value> repeat(Supplier<Stream<Value>> sourceFactory) {
        val out     = new LatestSlotStream<Value>();
        val stopped = new AtomicBoolean(false);
        val pump    = Thread.startVirtualThread(() -> {
                        try {
                            while (!stopped.get()) {
                                val src = sourceFactory.get();
                                pumpInto(src, out, stopped);
                            }
                        } catch (RuntimeException loopFailure) {
                            out.put(Value.error(messageOf(loopFailure)));
                        } finally {
                            out.complete();
                        }
                    });
        out.onClose(() -> {
            stopped.set(true);
            pump.interrupt();
        });
        return out;
    }

    /**
     * Stream that emits values from {@code source} but suppresses any
     * value equal to its immediate predecessor. The first value is
     * always emitted. Useful for polling sources whose value rarely
     * changes between polls.
     * <p>
     * Hot: spawns one virtual thread that pulls from {@code source}.
     * Caller must {@code close()} the returned stream.
     */
    public static Stream<Value> distinctUntilChanged(Stream<Value> source) {
        val out      = new LatestSlotStream<Value>();
        val stopped  = new AtomicBoolean(false);
        val previous = new AtomicReference<Value>();
        val pump     = Thread.startVirtualThread(() -> {
                         try {
                             while (!stopped.get()) {
                                 val v = source.awaitNext();
                                 if (v == null) {
                                     return;
                                 }
                                 val prev = previous.get();
                                 if (!Objects.equals(v, prev)) {
                                     previous.set(v);
                                     out.put(v);
                                 }
                             }
                         } catch (InterruptedException ie) {
                             Thread.currentThread().interrupt();
                         } catch (RuntimeException loopFailure) {
                             out.put(Value.error(messageOf(loopFailure)));
                         } finally {
                             out.complete();
                             source.close();
                         }
                     });
        out.onClose(() -> {
            stopped.set(true);
            pump.interrupt();
            source.close();
        });
        return out;
    }

    /**
     * Stream that emits {@code source.get()} immediately, then again
     * every {@code interval} until closed, scheduling each subsequent
     * emission via {@code scheduler}. Use when deterministic test
     * advancement is required ({@code TestTimeScheduler}); the simpler
     * {@link #poll(Duration, Supplier)} uses real-time {@code sleep}
     * and is not deterministically testable.
     * <p>
     * Hot: spawns nothing on its own but each tick reschedules itself
     * on {@code scheduler}. Caller must {@code close()} to cancel
     * pending ticks.
     */
    public static Stream<Value> scheduledPoll(Duration interval, Supplier<Value> source, Clock clock,
            TimeScheduler scheduler) {
        val s            = new LatestSlotStream<Value>();
        val stopped      = new AtomicBoolean(false);
        val cancelHolder = new AtomicReference<>(Cancellable.NOOP);
        val tick         = new AtomicReference<Runnable>();
        tick.set(() -> {
            if (stopped.get()) {
                return;
            }
            boolean supplierEmittedError = false;
            try {
                s.put(source.get());
            } catch (RuntimeException supplierFailure) {
                s.put(Value.error(messageOf(supplierFailure)));
                supplierEmittedError = true;
            }
            if (stopped.get()) {
                return;
            }
            try {
                val nextInstant = clock.instant().plus(interval);
                cancelHolder.set(scheduler.scheduleAt(nextInstant, tick.get()));
            } catch (RuntimeException reschedulingFailure) {
                // Preserve the supplier's diagnostic (the user-visible error) by not
                // overwriting it in the latest-wins slot. If only rescheduling failed,
                // that error reaches the consumer.
                if (!supplierEmittedError) {
                    s.put(Value.error(messageOf(reschedulingFailure)));
                }
                stopped.set(true);
                s.complete();
            }
        });
        tick.get().run();
        s.onClose(() -> {
            stopped.set(true);
            cancelHolder.get().cancel();
            s.complete();
        });
        return s;
    }

    /**
     * Producer signature for {@link Streams#fromCallback}. The
     * implementation must invoke {@code emit} for each value and
     * {@code complete} when no more values will follow. The returned
     * cleanup runs on stream close.
     */
    @FunctionalInterface
    public interface CallbackProducer {
        Runnable produce(Consumer<Value> emit, Runnable complete);
    }
}
