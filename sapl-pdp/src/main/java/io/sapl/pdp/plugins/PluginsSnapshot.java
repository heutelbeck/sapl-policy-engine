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

import io.sapl.api.pdp.plugins.PluginRequirement;

import java.util.Set;

/**
 * An immutable view of the plugins in effect, published by a
 * {@link PluginsSource}. A PDP configuration obtains its runtime bundle from
 * the snapshot, restricted to the plugins it requests. Implementations are
 * immutable and safe to use without synchronization.
 *
 * @since 4.2.0
 */
public interface PluginsSnapshot {

    /**
     * @return the ids of the plugins that are loaded and can be requested
     */
    Set<String> availablePluginIds();

    /**
     * @return the bundle carrying only base contributions, used where no
     * configuration-specific bundle exists, for example for error voters
     */
    PluginsBundle baseBundle();

    /**
     * Returns the bundle for a PDP: the base contributions plus the
     * contributions of the requested plugins. An empty request yields the
     * {@link #baseBundle()}.
     *
     * @param pdpId the PDP requesting the plugins
     * @param requirements the requested plugins, each with the range of
     * versions the PDP accepts
     * @return the bundle for the PDP
     * @throws PluginSelectionException if requested plugins are not available
     * in an accepted version or their contributions cannot be combined
     */
    PluginsBundle bundleFor(String pdpId, Set<PluginRequirement> requirements);
}
