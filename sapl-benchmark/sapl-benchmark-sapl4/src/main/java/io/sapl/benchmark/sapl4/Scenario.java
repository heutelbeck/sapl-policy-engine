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
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import lombok.val;

import java.util.List;
import java.util.function.Supplier;

/**
 * A benchmark scenario: policies, variables, subscriptions, and expected
 * decision.
 *
 * @param name scenario name (e.g., "rbac", "hospital-100")
 * @param policies SAPL policy document strings
 * @param variables PDP variables
 * @param algorithm combining algorithm
 * @param subscriptions authorization subscriptions to cycle through during
 * benchmarking; the first is used for sanity checking
 * @param expectedDecision expected result of the first subscription for sanity
 * checking
 */
public record Scenario(
        String name,
        Supplier<List<String>> policies,
        ObjectValue variables,
        CombiningAlgorithm algorithm,
        List<AuthorizationSubscription> subscriptions,
        AuthorizationDecision expectedDecision) {

    /**
     * Convenience constructor for scenarios with a single subscription.
     */
    Scenario(String name,
            Supplier<List<String>> policies,
            ObjectValue variables,
            CombiningAlgorithm algorithm,
            AuthorizationSubscription subscription,
            AuthorizationDecision expectedDecision) {
        this(name, policies, variables, algorithm, List.of(subscription), expectedDecision);
    }

    /**
     * Returns the first subscription, used for sanity checking.
     *
     * @return the sanity check subscription
     */
    AuthorizationSubscription subscription() {
        return subscriptions.getFirst();
    }

    /**
     * Builds an embedded PDP configured for this scenario.
     *
     * @param compilerOptions compiler options including indexing strategy
     * @return the PDP components (caller must dispose)
     */
    PDPComponents buildPdp(ObjectValue compilerOptions) {
        val pdpData          = new PdpData(variables, Value.EMPTY_OBJECT);
        val pdpConfiguration = new PDPConfiguration("default", name, algorithm, compilerOptions, policies.get(),
                pdpData);
        return PolicyDecisionPointBuilder.withDefaults().withConfiguration(pdpConfiguration).build();
    }

}
