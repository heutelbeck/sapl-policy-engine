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

import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StaticPluginsSnapshot")
class StaticPluginsSnapshotTests {

    private static final String PDP_ID = "test-pdp";

    private static StaticPluginsSnapshot snapshot() {
        return new StaticPluginsSnapshot(new PluginsBundle(new DefaultFunctionBroker()));
    }

    @Test
    @DisplayName("no plugins are available")
    void whenCreatedThenNoPluginsAreAvailable() {
        assertThat(snapshot().availablePluginIds()).isEmpty();
    }

    @Test
    @DisplayName("requesting no plugins yields the base bundle")
    void whenNoPluginsRequestedThenBaseBundleIsReturned() {
        val snapshot = snapshot();

        assertThat(snapshot.bundleFor(PDP_ID, Set.of())).isSameAs(snapshot.baseBundle());
    }

    @Test
    @DisplayName("requesting plugins fails, naming the PDP and all requested plugins in sorted order")
    void whenPluginsRequestedThenSelectionFails() {
        val snapshot  = snapshot();
        val requested = Set.of("second-plugin", "first-plugin");

        assertThatThrownBy(() -> snapshot.bundleFor(PDP_ID, requested)).isInstanceOf(PluginSelectionException.class)
                .hasMessageContaining(PDP_ID).hasMessageContaining("[first-plugin, second-plugin]");
    }
}
