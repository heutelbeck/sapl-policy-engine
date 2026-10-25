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
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.plugins.PluginRequirement;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.val;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable {@link PluginsSnapshot} published by the
 * {@link HotReloadingPluginsSource} on every republish. Holds the plugins that
 * are available after their Policy Information Points were loaded, together
 * with the base contributions.
 * <p>
 * The base bundle is built once on construction; a failure there is a failure
 * of the base libraries and propagates to the caller. Every other bundle is
 * built on request: {@link #bundleFor(String, Set)} creates a function broker
 * from the base libraries plus the libraries of the requested plugins, and
 * combines the base decision interceptors with those of the requested plugins.
 * The order of interceptors contributed by different plugins is unspecified.
 * Requesting an unavailable plugin, a plugin whose version lies outside the
 * requested version range, or plugins whose libraries cannot be combined,
 * fails with a {@link PluginSelectionException}.
 *
 * @since 4.2.0
 */
final class PluginEngineSnapshot implements PluginsSnapshot {

    private static final String ERROR_LIBRARIES_NOT_PROVIDABLE = "Function libraries of plugins %s cannot be provided for PDP '%s': %s";

    private final Map<String, LoadedPlugin>           plugins;
    private final List<Object>                        baseLibraries;
    private final boolean                             includeDefaultLibraries;
    private final int                                 functionCacheSize;
    private final List<DecisionInterceptor>           baseInterceptors;
    private final List<SubscriptionLifecycleListener> lifecycleListeners;
    private final PluginsBundle                       baseBundle;

    PluginEngineSnapshot(Map<String, LoadedPlugin> plugins,
            List<Object> baseLibraries,
            boolean includeDefaultLibraries,
            int functionCacheSize,
            List<DecisionInterceptor> baseInterceptors,
            List<SubscriptionLifecycleListener> lifecycleListeners) {
        this.plugins                 = Map.copyOf(plugins);
        this.baseLibraries           = List.copyOf(baseLibraries);
        this.includeDefaultLibraries = includeDefaultLibraries;
        this.functionCacheSize       = functionCacheSize;
        this.baseInterceptors        = List.copyOf(baseInterceptors);
        this.lifecycleListeners      = List.copyOf(lifecycleListeners);
        this.baseBundle              = new PluginsBundle(PolicyDecisionPointBuilder.buildFunctionBroker(
                functionCacheSize, includeDefaultLibraries, baseLibraries), baseInterceptors, lifecycleListeners);
    }

    @Override
    public Set<String> availablePluginIds() {
        return plugins.keySet();
    }

    @Override
    public PluginsBundle baseBundle() {
        return baseBundle;
    }

    @Override
    public PluginsBundle bundleFor(String pdpId, Set<PluginRequirement> requirements) {
        val missing = new HashSet<PluginRequirement>();
        for (val requirement : requirements) {
            val plugin = plugins.get(requirement.pluginId());
            if (plugin == null || !requirement.isSatisfiedBy(plugin.version())) {
                missing.add(requirement);
            }
        }

        if (!missing.isEmpty()) {
            throw new PluginSelectionException(pdpId, missing);
        }

        if (requirements.isEmpty()) {
            return baseBundle;
        }

        val libraries    = new ArrayList<>(baseLibraries);
        val interceptors = new ArrayList<>(baseInterceptors);
        for (val requirement : requirements) {
            val plugin = plugins.get(requirement.pluginId());
            libraries.addAll(plugin.functionLibraries());
            interceptors.addAll(plugin.decisionInterceptors());
        }

        try {
            val broker = PolicyDecisionPointBuilder.buildFunctionBroker(functionCacheSize, includeDefaultLibraries,
                    libraries);
            return new PluginsBundle(broker, interceptors, lifecycleListeners);

        } catch (RuntimeException e) {
            val pluginIds = requirements.stream().map(PluginRequirement::pluginId)
                    .collect(Collectors.toCollection(TreeSet::new));
            throw new PluginSelectionException(
                    ERROR_LIBRARIES_NOT_PROVIDABLE.formatted(pluginIds, pdpId, e.getMessage()), e);
        }
    }
}
