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
package io.sapl.compiler.document;

import io.sapl.api.model.EvaluationContext;

import java.util.Map;

/**
 * The sealed root of voter shapes. A {@link Vote} is itself a terminal
 * voter (its evaluation is itself). {@link PureVoter} evaluates
 * synchronously against an {@link EvaluationContext} and produces a
 * {@link Vote}. {@link StreamVoter} is the migration target for the
 * snapshot path; concrete StreamVoter records override
 * {@link StreamVoter#evaluate(EvaluationContext)}.
 */
public sealed interface Voter permits Vote, PureVoter, StreamVoter {

    /**
     * Snapshot-driven evaluation entry point. Dispatches by voter variant:
     * a {@link Vote} terminal returns itself with no dependencies; a
     * {@link PureVoter} runs its synchronous {@code vote(ctx)}; a
     * {@link StreamVoter} delegates to its own {@code evaluate(ctx)}
     * default which concrete records override.
     * <p>
     * Combiners and PDP wiring should call this method uniformly rather
     * than dispatching on the variant themselves.
     *
     * @param ctx evaluation context bound to a snapshot version
     * @return the per-round {@link VoteResult}: a {@link Vote} (or
     * {@code null} if the underlying evaluation could not complete)
     * plus the full dependency map this pass needed or touched
     *
     * @since 4.2.0
     */
    default VoteResult evaluate(EvaluationContext ctx) {
        return switch (this) {
        case Vote v        -> new VoteResult(v, Map.of());
        case PureVoter p   -> new VoteResult(p.vote(ctx), Map.of());
        case StreamVoter s -> s.evaluate(ctx);
        };
    }
}
