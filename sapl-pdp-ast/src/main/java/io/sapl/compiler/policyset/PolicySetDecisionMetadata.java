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
package io.sapl.compiler.policyset;

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.compiler.pdp.DecisionMetadata;
import io.sapl.compiler.policy.PolicyDecision;

import java.util.List;

/**
 * Metadata for a policy set decision.
 *
 * @param source the policy set that produced this decision
 * @param contributingPolicyDecisions decisions from contained policies that
 * contributed to the result
 * @param contributingAttributes see
 * {@link DecisionMetadata#contributingAttributes()}
 * @param error see {@link DecisionMetadata#error()}
 */
public record PolicySetDecisionMetadata(
        PolicySetMetadata source,
        List<PolicyDecision> contributingPolicyDecisions,
        List<AttributeRecord> contributingAttributes,
        ErrorValue error) implements DecisionMetadata {}
