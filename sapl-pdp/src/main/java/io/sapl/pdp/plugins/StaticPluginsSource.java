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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Single-snapshot {@link PluginsSource}. Holds one
 * {@link PluginsBundle} for the lifetime of the source. Subscribers
 * receive it synchronously on subscribe.
 *
 * @since 4.1.0
 */
public final class StaticPluginsSource implements PluginsSource {

    private final PluginsBundle                bundle;
    private final Set<Consumer<PluginsBundle>> listeners = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                closed    = new AtomicBoolean(false);
    private final Object                       lock      = new Object();

    public StaticPluginsSource(@NonNull PluginsBundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public void subscribe(@NonNull Consumer<PluginsBundle> listener) {
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            if (listeners.add(listener)) {
                listener.accept(bundle);
            }
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<PluginsBundle> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed.set(true);
            listeners.clear();
        }
    }

    int registeredListenerCount() {
        return listeners.size();
    }
}
