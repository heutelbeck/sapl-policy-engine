/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

public class Bitmask {

    private final BitSet impl;

    // force initial capacity?
    public Bitmask() {
        impl = new BitSet();
    }

    public Bitmask(final Bitmask mask) {
        impl = (BitSet) mask.impl.clone();
    }

    public void and(Bitmask mask) {
        impl.and(mask.impl);
    }

    public void andNot(Bitmask mask) {
        impl.andNot(mask.impl);
    }

    public void clear(int bitIndex) {
        impl.clear(bitIndex);
    }

    public void flip(int fromIndex, int toIndex) {
        impl.flip(fromIndex, toIndex);
    }

    public boolean intersects(Bitmask mask) {
        return impl.intersects(mask.impl);
    }

    public void or(Bitmask mask) {
        impl.or(mask.impl);
    }

    public void set(int bitIndex) {
        impl.set(bitIndex);
    }

    public void set(int fromIndex, int toIndex) {
        impl.set(fromIndex, toIndex);
    }

    public int numberOfBitsSet() {
        AtomicInteger numberOfBitsSets = new AtomicInteger();
        forEachSetBit(index -> numberOfBitsSets.getAndIncrement());

        return numberOfBitsSets.get();
    }

    public void forEachSetBit(final Consumer<Integer> action) {
        Preconditions.checkNotNull(action);
        impl.stream().forEach(action::accept);
    }

    public boolean isSet(int bitIndex) {
        return impl.get(bitIndex);
    }

    @Override
    public String toString() {
        return String.valueOf(impl.toString());
    }

}
