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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.model.Value;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-attribute stress PIP base. Each invocation registers a
 * single-slot stream into a per-attribute bucket so the test driver
 * can push values to every live invocation at once. Subclasses carry
 * the {@code @PolicyInformationPoint} annotation with a distinct
 * namespace so the broker treats them as separate PIPs.
 */
public abstract class StressPip {

    public static final List<String> ATTRIBUTES = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");

    private final String                                                     instanceTag;
    private final Map<String, CopyOnWriteArrayList<LatestSlotStream<Value>>> backings = new ConcurrentHashMap<>();
    private final AtomicLong                                                 opens    = new AtomicLong();
    private final AtomicLong                                                 closes   = new AtomicLong();

    protected StressPip() {
        this("default");
    }

    protected StressPip(String instanceTag) {
        this.instanceTag = instanceTag;
    }

    public String instanceTag() {
        return instanceTag;
    }

    public long opens() {
        return opens.get();
    }

    public long closes() {
        return closes.get();
    }

    public int liveBackings(String attribute) {
        val bucket = backings.get(attribute);
        return bucket == null ? 0 : bucket.size();
    }

    public int liveBackingsTotal() {
        return backings.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Pushes {@code value} into every active invocation for
     * {@code attribute}. No-op if no invocations are registered.
     */
    public void emit(String attribute, Value value) {
        val bucket = backings.get(attribute);
        if (bucket == null) {
            return;
        }
        for (val slot : bucket) {
            slot.put(value);
        }
    }

    /** Pushes {@code value} into every active invocation across all attributes. */
    public void emitAll(Value value) {
        for (val bucket : backings.values()) {
            for (val slot : bucket) {
                slot.put(value);
            }
        }
    }

    @EnvironmentAttribute
    public Stream<Value> a() {
        return register("a");
    }

    @EnvironmentAttribute
    public Stream<Value> b() {
        return register("b");
    }

    @EnvironmentAttribute
    public Stream<Value> c() {
        return register("c");
    }

    @EnvironmentAttribute
    public Stream<Value> d() {
        return register("d");
    }

    @EnvironmentAttribute
    public Stream<Value> e() {
        return register("e");
    }

    @EnvironmentAttribute
    public Stream<Value> f() {
        return register("f");
    }

    @EnvironmentAttribute
    public Stream<Value> g() {
        return register("g");
    }

    @EnvironmentAttribute
    public Stream<Value> h() {
        return register("h");
    }

    @EnvironmentAttribute
    public Stream<Value> i() {
        return register("i");
    }

    @EnvironmentAttribute
    public Stream<Value> j() {
        return register("j");
    }

    private Stream<Value> register(String attribute) {
        opens.incrementAndGet();
        val slot   = new LatestSlotStream<Value>();
        val bucket = backings.computeIfAbsent(attribute, k -> new CopyOnWriteArrayList<>());
        bucket.add(slot);
        slot.onClose(() -> {
            closes.incrementAndGet();
            bucket.remove(slot);
        });
        return slot;
    }
}
