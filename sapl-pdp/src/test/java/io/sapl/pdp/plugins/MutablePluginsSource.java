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
package io.sapl.pdp.plugins;

import lombok.NonNull;
import lombok.val;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Test fixture standing in for the future plugin engine. Each
 * {@link #publish(PluginsBundle)} call emits one atomic snapshot, carrying the
 * given bundle as its base bundle, to every subscriber.
 */
public final class MutablePluginsSource implements PluginsSource {

    private final AtomicReference<PluginsSnapshot> current   = new AtomicReference<>();
    private final Set<Consumer<PluginsSnapshot>>   listeners = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                    closed    = new AtomicBoolean(false);

    public MutablePluginsSource() {
    }

    public MutablePluginsSource(@NonNull PluginsBundle initial) {
        current.set(new StaticPluginsSnapshot(initial));
    }

    public void publish(@NonNull PluginsBundle bundle) {
        if (closed.get()) {
            return;
        }
        val snapshot = new StaticPluginsSnapshot(bundle);
        current.set(snapshot);
        for (var l : listeners) {
            l.accept(snapshot);
        }
    }

    /**
     * Emits the given snapshot as is, for tests that need a snapshot other than
     * one carrying only a base bundle.
     *
     * @param snapshot the snapshot to publish
     */
    public void publish(@NonNull PluginsSnapshot snapshot) {
        if (closed.get()) {
            return;
        }
        current.set(snapshot);
        for (var l : listeners) {
            l.accept(snapshot);
        }
    }

    public PluginsSnapshot current() {
        return current.get();
    }

    @Override
    public void subscribe(@NonNull Consumer<PluginsSnapshot> listener) {
        if (closed.get()) {
            return;
        }
        if (listeners.add(listener)) {
            var snapshot = current.get();
            if (snapshot != null) {
                listener.accept(snapshot);
            }
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<PluginsSnapshot> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        closed.set(true);
        listeners.clear();
    }
}
