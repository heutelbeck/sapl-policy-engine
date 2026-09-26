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

import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.plugins.SaplDecisionInterceptorPlugin;
import io.sapl.api.pdp.plugins.SaplFunctionLibraryPlugin;
import io.sapl.api.pdp.plugins.SaplPolicyInformationPointPlugin;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SaplPluginManager groups the extensions of started plugins by plugin id")
class SaplPluginManagerTests {

    private static final Path PLUGINS_PATH = Path.of("plugins");

    private static final String LIBRARY_PLUGIN = "library-plugin";
    private static final String PIP_PLUGIN     = "pip-plugin";
    private static final String FAILING_PLUGIN = "failing-plugin";

    private static final Object              FUNCTION_LIBRARY         = new Object();
    private static final Object              POLICY_INFORMATION_POINT = new Object();
    private static final DecisionInterceptor DECISION_INTERCEPTOR     = (decision, timestamp, subscriptionId,
            subscription) -> {};

    /**
     * An extension implementing two extension points at once, which PF4J
     * allows for a single extension class.
     */
    private record LibraryAndPipExtension(
            Collection<Object> functionLibraries,
            Collection<Object> policyInformationPoints)
            implements SaplFunctionLibraryPlugin, SaplPolicyInformationPointPlugin {}

    private static SaplFunctionLibraryPlugin libraryExtension(Object... libraries) {
        return () -> List.of(libraries);
    }

    private static SaplPolicyInformationPointPlugin pipExtension(Object... pips) {
        return () -> List.of(pips);
    }

    private static SaplDecisionInterceptorPlugin interceptorExtension(DecisionInterceptor... interceptors) {
        return () -> List.of(interceptors);
    }

    /**
     * Builds a manager over a mocked PF4J plugin manager whose started plugins
     * are the keys of the given map, each providing the listed extensions.
     */
    private static SaplPluginManager managerWith(Map<String, List<Object>> extensionsByPluginId) {
        val pluginManager  = mock(PluginManager.class);
        val startedPlugins = new ArrayList<PluginWrapper>();
        extensionsByPluginId.forEach((pluginId, extensions) -> {
            val wrapper = mock(PluginWrapper.class);
            when(wrapper.getPluginId()).thenReturn(pluginId);
            startedPlugins.add(wrapper);
            when(pluginManager.getExtensions(pluginId)).thenReturn(extensions);
        });
        when(pluginManager.getStartedPlugins()).thenReturn(startedPlugins);
        return new SaplPluginManager(pluginManager, PLUGINS_PATH);
    }

    @Nested
    @DisplayName("grouping")
    class Grouping {

        @Test
        @DisplayName("each started plugin yields one entry carrying exactly its own libraries, PIPs and interceptors")
        void whenTwoPluginsStartedThenEachPluginCarriesOnlyItsOwnExtensions() {
            val extensions = new LinkedHashMap<String, List<Object>>();
            extensions.put(LIBRARY_PLUGIN, List.of(libraryExtension(FUNCTION_LIBRARY)));
            extensions.put(PIP_PLUGIN,
                    List.of(pipExtension(POLICY_INFORMATION_POINT), interceptorExtension(DECISION_INTERCEPTOR)));
            val manager = managerWith(extensions);

            val plugins = manager.loadedPlugins();

            assertThat(plugins).containsOnlyKeys(LIBRARY_PLUGIN, PIP_PLUGIN);
            assertThat(plugins.get(LIBRARY_PLUGIN)).satisfies(plugin -> {
                assertThat(plugin.pluginId()).isEqualTo(LIBRARY_PLUGIN);
                assertThat(plugin.functionLibraries()).containsExactly(FUNCTION_LIBRARY);
                assertThat(plugin.policyInformationPoints()).isEmpty();
                assertThat(plugin.decisionInterceptors()).isEmpty();
            });
            assertThat(plugins.get(PIP_PLUGIN)).satisfies(plugin -> {
                assertThat(plugin.pluginId()).isEqualTo(PIP_PLUGIN);
                assertThat(plugin.functionLibraries()).isEmpty();
                assertThat(plugin.policyInformationPoints()).containsExactly(POLICY_INFORMATION_POINT);
                assertThat(plugin.decisionInterceptors()).containsExactly(DECISION_INTERCEPTOR);
            });
        }

        @Test
        @DisplayName("no started plugins yield an empty result")
        void whenNoPluginsStartedThenResultIsEmpty() {
            val manager = managerWith(Map.of());

            assertThat(manager.loadedPlugins()).isEmpty();
        }

        @Test
        @DisplayName("an extension implementing several extension points is collected for each of them")
        void whenExtensionImplementsSeveralExtensionPointsThenAllRolesAreCollected() {
            val extension = new LibraryAndPipExtension(List.of(FUNCTION_LIBRARY), List.of(POLICY_INFORMATION_POINT));
            val manager   = managerWith(Map.of(LIBRARY_PLUGIN, List.of(extension)));

            val plugin = manager.loadedPlugins().get(LIBRARY_PLUGIN);

            assertThat(plugin.functionLibraries()).containsExactly(FUNCTION_LIBRARY);
            assertThat(plugin.policyInformationPoints()).containsExactly(POLICY_INFORMATION_POINT);
        }

        @Test
        @DisplayName("an extension of an unrelated extension point is ignored without dropping the plugin")
        void whenExtensionIsOfUnrelatedTypeThenItIsIgnored() {
            val manager = managerWith(Map.of(LIBRARY_PLUGIN, List.of(new Object())));

            val plugins = manager.loadedPlugins();

            assertThat(plugins).containsOnlyKeys(LIBRARY_PLUGIN);
            assertThat(plugins.get(LIBRARY_PLUGIN)).satisfies(plugin -> {
                assertThat(plugin.functionLibraries()).isEmpty();
                assertThat(plugin.policyInformationPoints()).isEmpty();
                assertThat(plugin.decisionInterceptors()).isEmpty();
            });
        }

        @Test
        @DisplayName("the returned map cannot be modified")
        void whenModifyingResultThenUnsupportedOperation() {
            val plugins = managerWith(Map.of(LIBRARY_PLUGIN, List.of(libraryExtension(FUNCTION_LIBRARY))))
                    .loadedPlugins();

            assertThatThrownBy(() -> plugins.remove(LIBRARY_PLUGIN)).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("failure isolation")
    class FailureIsolation {

        @Test
        @DisplayName("a plugin whose extension throws is omitted while other plugins remain available")
        void whenExtensionThrowsThenOnlyThatPluginIsOmitted() {
            val failing    = (SaplPolicyInformationPointPlugin) () -> {
                               throw new IllegalStateException("Extension failed.");
                           };
            val extensions = new LinkedHashMap<String, List<Object>>();
            extensions.put(LIBRARY_PLUGIN, List.of(libraryExtension(FUNCTION_LIBRARY)));
            extensions.put(FAILING_PLUGIN, List.of(failing));
            val manager = managerWith(extensions);

            val plugins = manager.loadedPlugins();

            assertThat(plugins).containsOnlyKeys(LIBRARY_PLUGIN);
        }

        @Test
        @DisplayName("a plugin providing a null element is omitted while other plugins remain available")
        void whenExtensionProvidesNullElementThenOnlyThatPluginIsOmitted() {
            val withNull   = (SaplFunctionLibraryPlugin) () -> Arrays.asList(FUNCTION_LIBRARY, null);
            val extensions = new LinkedHashMap<String, List<Object>>();
            extensions.put(PIP_PLUGIN, List.of(pipExtension(POLICY_INFORMATION_POINT)));
            extensions.put(FAILING_PLUGIN, List.of(withNull));
            val manager = managerWith(extensions);

            val plugins = manager.loadedPlugins();

            assertThat(plugins).containsOnlyKeys(PIP_PLUGIN);
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("loaded plugins cannot be queried after the manager is closed")
        void whenManagerClosedThenLoadedPluginsThrows() {
            val manager = managerWith(Map.of(LIBRARY_PLUGIN, List.of(libraryExtension(FUNCTION_LIBRARY))));
            manager.close();

            assertThatThrownBy(manager::loadedPlugins).isInstanceOf(IllegalStateException.class);
        }
    }
}
