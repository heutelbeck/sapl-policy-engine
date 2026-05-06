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
package io.sapl.pdp.configuration.source;

import io.sapl.api.pdp.configuration.PDPConfiguration;

import java.util.function.Consumer;

/**
 * A source of PDP configurations. Loads policies from somewhere
 * (filesystem, classpath, signed bundle, remote HTTP) and emits
 * {@link ConfigurationEvent}s to subscribed listeners as
 * configurations appear, change, or are removed.
 * <p>
 * Sources are dormant on construction. The first call to
 * {@link #subscribe(Consumer)} activates the source: any initial
 * load fires synchronously to the new subscriber, and ongoing
 * change-monitoring (file watcher, HTTP poll loop) starts.
 * Additional subscribers join the live stream and see only events
 * that occur after they subscribe.
 * <p>
 * Extends {@link AutoCloseable} so Spring auto-detects the bean and
 * calls {@code close()} on shutdown, and so callers can use
 * try-with-resources.
 *
 * @since 4.1.0
 */
public interface PDPConfigurationSource extends AutoCloseable {

    /**
     * Adds a listener that receives configuration events from this
     * source. The first subscribe activates the source; subsequent
     * subscribes join the live stream.
     *
     * @param listener the listener to add
     */
    void subscribe(Consumer<ConfigurationEvent> listener);

    /**
     * Removes a previously-registered listener. Does nothing if the
     * listener was not registered. Does not deactivate the source
     * even if no listeners remain; use {@link #close()} for that.
     *
     * @param listener the listener to remove (must be the same
     * reference passed to {@link #subscribe(Consumer)})
     */
    void unsubscribe(Consumer<ConfigurationEvent> listener);

    /**
     * Returns whether this source has been closed.
     *
     * @return {@code true} if {@link #close()} has been called
     */
    boolean isClosed();

    /**
     * Events emitted by a {@link PDPConfigurationSource}.
     */
    sealed interface ConfigurationEvent {

        /**
         * A configuration was loaded or reloaded.
         *
         * @param configuration the new configuration
         * @param keepOldOnError if {@code true}, the consumer should
         * retain the previous configuration if the new one fails to
         * compile; if {@code false}, the consumer should replace the
         * previous configuration with an error voter on compile
         * failure
         */
        record Load(PDPConfiguration configuration, boolean keepOldOnError) implements ConfigurationEvent {}

        /**
         * A previously-loaded configuration was removed (e.g., its
         * source directory was deleted in a multi-source layout).
         *
         * @param pdpId the identifier of the removed configuration
         */
        record Remove(String pdpId) implements ConfigurationEvent {}
    }
}
