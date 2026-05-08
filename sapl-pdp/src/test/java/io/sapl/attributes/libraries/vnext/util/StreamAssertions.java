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
package io.sapl.attributes.libraries.vnext.util;

import io.sapl.api.model.Stream;
import lombok.val;
import org.assertj.core.api.AbstractAssert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * AssertJ-style fluent assertions for {@link Stream}{@code <T>}.
 * Replaces typical {@code StepVerifier} usage in PIP tests:
 *
 * <pre>{@code
 * StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(1)).awaitsNext(Value.of("permit"))
 *         .awaitsNext(value -> assertThat(value.asString()).startsWith("p")).awaitsCompletion();
 * }</pre>
 *
 * @param <T> the value type of the stream under test
 */
public final class StreamAssertions<T> extends AbstractAssert<StreamAssertions<T>, Stream<T>> {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private Duration timeout = DEFAULT_TIMEOUT;

    private StreamAssertions(Stream<T> actual) {
        super(actual, StreamAssertions.class);
    }

    public static <T> StreamAssertions<T> assertThat(Stream<T> stream) {
        return new StreamAssertions<>(stream);
    }

    /**
     * Sets the per-await timeout for subsequent expectations. Default
     * is two seconds.
     */
    public StreamAssertions<T> withinTimeout(Duration newTimeout) {
        this.timeout = newTimeout;
        return this;
    }

    /**
     * Asserts that the next value emitted equals {@code expected},
     * within the configured timeout.
     */
    public StreamAssertions<T> awaitsNext(T expected) {
        val actualValue = pollOrFail("awaitsNext(" + expected + ")");
        if (!Objects.equals(expected, actualValue)) {
            failWithMessage("Expected next value to be <%s> but was <%s>", expected, actualValue);
        }
        return this;
    }

    /**
     * Asserts that the next emitted value satisfies {@code requirements}.
     */
    public StreamAssertions<T> awaitsNext(Consumer<T> requirements) {
        val actualValue = pollOrFail("awaitsNext(<assertion>)");
        requirements.accept(actualValue);
        return this;
    }

    /**
     * Asserts that the stream completes within the configured timeout
     * without producing further values.
     */
    public StreamAssertions<T> awaitsCompletion() {
        val completed = new AtomicBoolean(false);
        val emitted   = new AtomicReference<T>();
        val waiter    = Thread.startVirtualThread(() -> {
                          try {
                              T v = actual.awaitNext();
                              if (v == null) {
                                  completed.set(true);
                              } else {
                                  emitted.set(v);
                              }
                          } catch (InterruptedException ie) {
                              Thread.currentThread().interrupt();
                          }
                      });
        try {
            waiter.join(timeout.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            failWithMessage("Interrupted while waiting for completion");
            return this;
        }
        if (waiter.isAlive()) {
            waiter.interrupt();
            failWithMessage("Timed out after %s waiting for completion", timeout);
            return this;
        }
        if (emitted.get() != null) {
            failWithMessage("Expected stream to complete but it emitted <%s>", emitted.get());
        }
        if (!completed.get()) {
            failWithMessage("Expected stream to complete (saw neither value nor completion)");
        }
        return this;
    }

    /**
     * Drains every value the stream emits until completion or
     * timeout, returning them as a list. Closes the stream after.
     */
    public List<T> drain() {
        val collected = new ArrayList<T>();
        val deadline  = System.nanoTime() + timeout.toNanos();
        try (val stream = actual) {
            while (System.nanoTime() < deadline) {
                val maybe = stream.tryNext();
                if (maybe.isPresent()) {
                    collected.add(maybe.get());
                    continue;
                }
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return collected;
    }

    private T pollOrFail(String op) {
        val deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            val maybe = actual.tryNext();
            if (maybe.isPresent()) {
                return maybe.get();
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                failWithMessage("Interrupted while waiting for %s", op);
                return null;
            }
        }
        failWithMessage("Timed out after %s waiting for %s", timeout, op);
        return null;
    }
}
