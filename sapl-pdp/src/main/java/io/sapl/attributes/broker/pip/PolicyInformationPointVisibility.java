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

import java.util.List;
import java.util.Set;

/**
 * Controls which plugins' Policy Information Points a PDP can see. PIPs that do
 * not come from a plugin, the base PIPs, are visible to every PDP.
 *
 * @since 4.2.0
 */
public interface PolicyInformationPointVisibility {

    /** Sets the plugins whose PIPs are visible to the PDP. Base PIPs are always visible. */
    void setVisiblePlugins(String pdpId, Set<String> pluginIds);

    /** Equivalent to setVisiblePlugins(pdpId, Set.of()). */
    void clearVisiblePlugins(String pdpId);

    /**
     * Describes the collisions between Policy Information Points of different
     * plugins in the given set.
     *
     * @param pluginIds the ids of the plugins requested together
     * @return one description per colliding attribute pair; empty if the plugins
     * can be enabled together
     */
    List<String> conflictsAmong(Set<String> pluginIds);

    /** No-op for setups without a plugin engine. */
    PolicyInformationPointVisibility NOOP = new PolicyInformationPointVisibility() {
        @Override
        public void setVisiblePlugins(String pdpId, Set<String> pluginIds) {
            // no plugins, nothing to make visible
        }

        @Override
        public void clearVisiblePlugins(String pdpId) {
            // no plugins, nothing to hide
        }

        @Override
        public List<String> conflictsAmong(Set<String> pluginIds) {
            return List.of();
        }
    };
}
