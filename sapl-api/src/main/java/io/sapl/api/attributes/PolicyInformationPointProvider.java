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
package io.sapl.api.attributes;

import java.util.Collection;

/**
 * Provider interface for Policy Information Point instances.
 * <p>
 * Use this interface to register Policy Information Points supplied by an
 * extension module. Each entry is a fully
 * constructed instance whose class carries the {@link PolicyInformationPoint}
 * annotation; the attribute broker reads
 * its annotated methods reflectively.
 * <p>
 * This is the attribute-side counterpart of
 * {@link io.sapl.api.functions.FunctionLibraryProvider}.
 * <p>
 * Example:
 *
 * <pre>{@code
 * @Bean
 * PolicyInformationPointProvider additionalPips() {
 *     return () -> List.of(new GeographicPolicyInformationPoint(), new WeatherPolicyInformationPoint());
 * }
 * }</pre>
 */
@FunctionalInterface
public interface PolicyInformationPointProvider {

    /**
     * Returns the collection of Policy Information Point instances to register.
     *
     * @return collection of Policy Information Point instances
     */
    Collection<Object> policyInformationPoints();

}
