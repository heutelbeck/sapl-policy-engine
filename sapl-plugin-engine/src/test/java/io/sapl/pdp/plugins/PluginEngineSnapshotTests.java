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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("PluginEngineSnapshot builds a bundle per requested plugin set")
class PluginEngineSnapshotTests {

    private static final String PDP_ID        = "test-pdp";
    private static final String FIRST_PLUGIN  = "first-plugin";
    private static final String SECOND_PLUGIN = "second-plugin";

    private static final DecisionInterceptor           BASE_INTERCEPTOR   = mock(DecisionInterceptor.class);
    private static final DecisionInterceptor           FIRST_INTERCEPTOR  = mock(DecisionInterceptor.class);
    private static final DecisionInterceptor           SECOND_INTERCEPTOR = mock(DecisionInterceptor.class);
    private static final SubscriptionLifecycleListener BASE_LISTENER      = mock(SubscriptionLifecycleListener.class);

    @FunctionLibrary(name = "base", description = "Base test library")
    public static class BaseLibrary {
        @Function(docs = "Returns a constant")
        public static Value constant() {
            return Value.of("base");
        }
    }

    @FunctionLibrary(name = "first", description = "First test library")
    public static class FirstLibrary {
        @Function(docs = "Returns a constant")
        public static Value constant() {
            return Value.of("first");
        }
    }

    @FunctionLibrary(name = "second", description = "Second test library")
    public static class SecondLibrary {
        @Function(docs = "Returns a constant")
        public static Value constant() {
            return Value.of("second");
        }
    }

    /** Defines the same function as {@link FirstLibrary}, so the two cannot be combined. */
    @FunctionLibrary(name = "first", description = "Library colliding with the first test library")
    public static class CollidingLibrary {
        @Function(docs = "Returns a constant")
        public static Value constant() {
            return Value.of("colliding");
        }
    }

    private static LoadedPlugin plugin(String pluginId, Object library, DecisionInterceptor interceptor) {
        return new LoadedPlugin(pluginId, List.of(library), List.of(), List.of(interceptor));
    }

    private static PluginEngineSnapshot snapshotWith(Map<String, LoadedPlugin> plugins) {
        return new PluginEngineSnapshot(plugins, List.of(new BaseLibrary()), false, 0, List.of(BASE_INTERCEPTOR),
                List.of(BASE_LISTENER));
    }

    private static PluginEngineSnapshot snapshotWithFirstAndSecond() {
        return snapshotWith(Map.of(FIRST_PLUGIN, plugin(FIRST_PLUGIN, new FirstLibrary(), FIRST_INTERCEPTOR),
                SECOND_PLUGIN, plugin(SECOND_PLUGIN, new SecondLibrary(), SECOND_INTERCEPTOR)));
    }

    @Nested
    @DisplayName("base contributions")
    class BaseContributions {

        @Test
        @DisplayName("the available plugin ids are the ids of the given plugins")
        void whenCreatedThenAvailablePluginIdsAreThePluginIds() {
            assertThat(snapshotWithFirstAndSecond().availablePluginIds()).containsExactlyInAnyOrder(FIRST_PLUGIN,
                    SECOND_PLUGIN);
        }

        @Test
        @DisplayName("the base bundle carries only the base contributions")
        void whenCreatedThenBaseBundleCarriesOnlyBaseContributions() {
            val baseBundle = snapshotWithFirstAndSecond().baseBundle();

            assertThat(baseBundle.functionBroker().getRegisteredLibraries()).containsExactly(BaseLibrary.class);
            assertThat(baseBundle.decisionInterceptors()).containsExactly(BASE_INTERCEPTOR);
            assertThat(baseBundle.lifecycleListeners()).containsExactly(BASE_LISTENER);
        }

        @Test
        @DisplayName("requesting no plugins yields the base bundle")
        void whenNoPluginsRequestedThenBaseBundleIsReturned() {
            val snapshot = snapshotWithFirstAndSecond();

            assertThat(snapshot.bundleFor(PDP_ID, Set.of())).isSameAs(snapshot.baseBundle());
        }

        @Test
        @DisplayName("an invalid base library fails the construction")
        void whenBaseLibraryIsInvalidThenConstructionFails() {
            val notALibrary = new Object();

            assertThatThrownBy(
                    () -> new PluginEngineSnapshot(Map.of(), List.of(notALibrary), false, 0, List.of(), List.of()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("requested plugins")
    class RequestedPlugins {

        @Test
        @DisplayName("the bundle carries the base contributions plus those of the requested plugin only")
        void whenPluginRequestedThenBundleCarriesBaseAndRequestedContributions() {
            val bundle = snapshotWithFirstAndSecond().bundleFor(PDP_ID, Set.of(FIRST_PLUGIN));

            assertThat(bundle.functionBroker().getRegisteredLibraries()).containsExactlyInAnyOrder(BaseLibrary.class,
                    FirstLibrary.class);
            assertThat(bundle.decisionInterceptors()).containsExactlyInAnyOrder(BASE_INTERCEPTOR, FIRST_INTERCEPTOR);
            assertThat(bundle.lifecycleListeners()).containsExactly(BASE_LISTENER);
        }

        @Test
        @DisplayName("missing plugins fail the selection, naming every missing plugin but no available one")
        void whenRequestedPluginsAreMissingThenSelectionFailsNamingAllMissing() {
            val snapshot  = snapshotWithFirstAndSecond();
            val requested = Set.of(FIRST_PLUGIN, "missing-plugin-a", "missing-plugin-b");

            assertThatThrownBy(() -> snapshot.bundleFor(PDP_ID, requested)).isInstanceOf(PluginSelectionException.class)
                    .hasMessageContaining(PDP_ID).hasMessageContaining("[missing-plugin-a, missing-plugin-b]")
                    .hasMessageNotContaining(FIRST_PLUGIN);
        }

        @Test
        @DisplayName("plugins with colliding libraries can each be requested alone but not together")
        void whenCollidingPluginsRequestedTogetherThenSelectionFails() {
            val snapshot  = snapshotWith(
                    Map.of(FIRST_PLUGIN, plugin(FIRST_PLUGIN, new FirstLibrary(), FIRST_INTERCEPTOR), SECOND_PLUGIN,
                            plugin(SECOND_PLUGIN, new CollidingLibrary(), SECOND_INTERCEPTOR)));
            val requested = Set.of(FIRST_PLUGIN, SECOND_PLUGIN);

            assertThat(snapshot.bundleFor(PDP_ID, Set.of(FIRST_PLUGIN))).isNotNull();
            assertThat(snapshot.bundleFor(PDP_ID, Set.of(SECOND_PLUGIN))).isNotNull();
            assertThatThrownBy(() -> snapshot.bundleFor(PDP_ID, requested)).isInstanceOf(PluginSelectionException.class)
                    .hasMessageContaining(PDP_ID).hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }
}
