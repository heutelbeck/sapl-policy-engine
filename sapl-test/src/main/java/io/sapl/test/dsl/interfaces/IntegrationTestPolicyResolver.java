/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interfaces;

/**
 * This Interface can be used to define custom logic for resolving policies and
 * a PDP configuration to be used in an integration test. Use this in a class
 * derived from {@link io.sapl.test.dsl.setup.BaseTestAdapter} to customize the
 * resolving logic.
 */
public interface IntegrationTestPolicyResolver {
    /**
     * Allows to resolve an identifier to a {@link io.sapl.grammar.sapl.Policy} in
     * plain text.
     *
     * @param identifier The identifier used in an integration test definition via
     *                   {@link io.sapl.test.grammar.sapltest.PoliciesByInputString}.
     * @return The resolved Policy in plain text used for the test.
     */
    String resolvePolicyByIdentifier(String identifier);

    /**
     * Allows to resolve an identifier to a
     * {@link io.sapl.pdp.config.PDPConfiguration} in plain text.
     *
     * @param identifier The identifier used in an integration test definition via
     *                   {@link io.sapl.test.grammar.sapltest.PoliciesByInputString}.
     * @return The resolved PDPConfiguration in plain text used for the test.
     */
    String resolvePDPConfigurationByIdentifier(String identifier);

    /**
     * Allows to resolve a single identifier to a List of policies and a PDP
     * configuration.
     *
     * @param identifier The identifier used in an integration test definition via
     *                   {@link io.sapl.test.grammar.sapltest.PoliciesByIdentifier}.
     * @return The resolved Policies and PDP configuration in plain text to be used
     *         for the test.
     */
    IntegrationTestConfiguration resolveConfigurationByIdentifier(String identifier);
}
