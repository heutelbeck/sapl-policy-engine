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
 * Snapshot without plugins, used where no plugin engine is present. Every
 * configuration receives the base bundle; requesting any plugin fails with a
 * {@link PluginSelectionException} naming all requested plugins.
 *
 * @param baseBundle the bundle carrying the base contributions
 *
 * @since 4.2.0
 */
record StaticPluginsSnapshot(PluginsBundle baseBundle) implements PluginsSnapshot {
    @Override
    public Set<String> availablePluginIds() {
        return Set.of();
    }

    @Override
    public PluginsBundle bundleFor(String pdpId, Set<PluginRequirement> requirements) {
        if (!requirements.isEmpty()) {
            throw new PluginSelectionException(pdpId, requirements);
        }
        return baseBundle;
    }
}
