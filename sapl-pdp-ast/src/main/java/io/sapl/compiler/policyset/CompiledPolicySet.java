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

import io.sapl.api.model.CompiledExpression;
import io.sapl.compiler.pdp.CompiledDocument;
import io.sapl.compiler.pdp.VoteWithCoverage;
import io.sapl.compiler.pdp.PolicySetVoterMetadata;
import io.sapl.compiler.pdp.Voter;
import reactor.core.publisher.Flux;

/**
 * A compiled policy set.
 *
 * @param isApplicable see {@link CompiledDocument#isApplicable()}
 * @param voter see {@link CompiledDocument#voter()}
 * @param applicabilityAndVote see
 * {@link CompiledDocument#applicabilityAndVote()}
 * @param coverage stream emitting decisions with coverage data for testing
 * @param metadata policy set name and location for tracing
 * @param hasConstraints true if any contained policy has obligations, advice,
 * or a transformation
 */
public record CompiledPolicySet(
        CompiledExpression isApplicable,
        Voter voter,
        Voter applicabilityAndVote,
        Flux<VoteWithCoverage> coverage,
        PolicySetVoterMetadata metadata,
        boolean hasConstraints) implements CompiledDocument {}
