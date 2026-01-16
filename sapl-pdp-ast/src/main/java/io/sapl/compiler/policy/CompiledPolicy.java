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
package io.sapl.compiler.policy;

import io.sapl.api.model.CompiledExpression;
import io.sapl.compiler.pdp.CompiledDocument;
import io.sapl.compiler.pdp.DecisionMaker;
import reactor.core.publisher.Flux;

/**
 * A compiled policy extending {@link CompiledDocument} with coverage tracking
 * and metadata.
 *
 * @param isApplicable see {@link CompiledDocument#isApplicable()}
 * @param decisionMaker see {@link CompiledDocument#decisionMaker()}
 * @param applicabilityAndDecision see
 * {@link CompiledDocument#applicabilityAndDecision()}
 * @param coverage stream emitting decisions with coverage data for testing
 * @param metadata policy name, location, and entitlement for tracing
 * @param hasConstraints true if the policy has obligations, advice, or a
 * transformation
 */
public record CompiledPolicy(
        CompiledExpression isApplicable,
        DecisionMaker decisionMaker,
        DecisionMaker applicabilityAndDecision,
        Flux<PolicyDecisionWithCoverage> coverage,
        PolicyMetadata metadata,
        boolean hasConstraints) implements CompiledDocument {}
