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
import lombok.NonNull;
import org.semver4j.Semver;

import java.util.List;

/**
 * What one started plugin provides, keyed by its PF4J {@code Plugin-Id}: its
 * function libraries, Policy Information Points and decision interceptors.
 * The lists are immutable copies.
 *
 * @param pluginId the {@code Plugin-Id} from the plugin manifest
 * @param version the {@code Plugin-Version} from the plugin manifest, parsed
 * as a semantic version
 * @param functionLibraries the function library instances of the plugin
 * @param policyInformationPoints the Policy Information Point instances of the
 * plugin
 * @param decisionInterceptors the decision interceptors of the plugin
 *
 * @since 4.2.0
 */
public record LoadedPlugin(
        @NonNull String pluginId,
        @NonNull Semver version,
        List<Object> functionLibraries,
        List<Object> policyInformationPoints,
        List<DecisionInterceptor> decisionInterceptors) {

    public LoadedPlugin {
        functionLibraries       = List.copyOf(functionLibraries);
        policyInformationPoints = List.copyOf(policyInformationPoints);
        decisionInterceptors    = List.copyOf(decisionInterceptors);
    }
}
