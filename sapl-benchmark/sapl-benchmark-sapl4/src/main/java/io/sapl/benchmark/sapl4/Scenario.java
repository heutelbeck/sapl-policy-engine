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
package io.sapl.benchmark.sapl4;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.*;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;

import java.util.List;
import java.util.function.Supplier;

/**
 * A benchmark scenario: policies, variables, subscription, and expected
 * decision.
 *
 * @param name scenario name (e.g., "rbac", "simple-100")
 * @param policies SAPL policy document strings
 * @param variables PDP variables
 * @param algorithm combining algorithm
 * @param subscription the authorization subscription to evaluate
 * @param expectedDecision expected result for sanity checking
 */
record Scenario(
        String name,
        Supplier<List<String>> policies,
        ObjectValue variables,
        CombiningAlgorithm algorithm,
        AuthorizationSubscription subscription,
        AuthorizationDecision expectedDecision) {

    /**
     * Builds an embedded PDP configured for this scenario.
     *
     * @return the PDP components (caller must dispose)
     */
    PDPComponents buildPdp() {
        var pdpData          = new PdpData(variables, Value.EMPTY_OBJECT);
        var pdpConfiguration = new PDPConfiguration("default", name, algorithm, policies.get(), pdpData);
        return PolicyDecisionPointBuilder.withDefaults().withConfiguration(pdpConfiguration).build();
    }

}
