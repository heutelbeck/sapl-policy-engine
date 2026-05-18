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

import java.util.function.Consumer;

/**
 * A source of {@link PluginsBundle} snapshots. Each emitted bundle is
 * an immutable value representing the plugin contributions in effect.
 * Subscribers receive the current bundle synchronously on subscribe
 * and any further bundles as the producer emits them.
 * <p>
 * Trivial implementations emit once at construction and stand idle.
 * Plugin-engine implementations emit again on every catalog change.
 *
 * @since 4.1.0
 */
public interface PluginsSource extends AutoCloseable {

    /**
     * Registers {@code listener} and delivers the current bundle to it
     * synchronously if one has been published.
     *
     * @param listener the listener to add
     */
    void subscribe(Consumer<PluginsBundle> listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    void unsubscribe(Consumer<PluginsBundle> listener);

    /**
     * @return {@code true} after {@link #close()} has been called
     */
    boolean isClosed();

    /**
     * Releases listeners. Idempotent.
     */
    @Override
    void close();
}
