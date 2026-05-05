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
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.policy.CoverageVoter;
import reactor.core.publisher.Flux;

/**
 * Compiled form of a SAPL policy set. Carries two coverage paths during
 * the snapshot migration:
 * <ul>
 * <li>{@code coverage} is the legacy Reactor stream produced by the
 * combining-algorithm compilers that have not yet migrated to the
 * snapshot model. Migrated algorithms (e.g. FIRST) populate this field
 * with a {@code Flux.error} placeholder so any caller still reaching
 * for the Reactor pipeline fails loudly.</li>
 * <li>{@code coverageVoter} is the snapshot-driven coverage voter,
 * consumed via {@code coverageVoter().evaluate(ctx)}. Algorithms not
 * yet migrated populate it with a {@link CoverageVoter.NotMigrated}
 * stub that throws on evaluate.</li>
 * </ul>
 * The Flux field will be removed once all four combining algorithms
 * have migrated their coverage path to {@link CoverageVoter}.
 */
public record CompiledPolicySet(
        CompiledExpression isApplicable,
        Voter voter,
        Voter applicabilityAndVote,
        Flux<VoteWithCoverage> coverage,
        CoverageVoter coverageVoter,
        PolicySetVoterMetadata metadata) implements CompiledDocument {}
