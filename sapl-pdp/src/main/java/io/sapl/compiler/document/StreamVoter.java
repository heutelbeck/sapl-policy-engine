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
import reactor.core.publisher.Flux;

public non-sealed interface StreamVoter extends Voter {

    /**
     * Reactor-based evaluation entry point. The Reactor pipeline is
     * being removed at the voter layer; new code uses
     * {@link #evaluate(EvaluationContext)} with the snapshot-driven
     * model. The default throws {@link UnsupportedOperationException}
     * naming the concrete class, so any code path still reaching for
     * the Reactor pipeline fails loudly at runtime rather than silently
     * producing degraded results.
     */
    default Flux<Vote> vote() {
        throw new UnsupportedOperationException(
                "Reactor vote() removed; use evaluate(EvaluationContext) on " + getClass().getSimpleName());
    }

    /**
     * Snapshot-driven evaluation entry point. Concrete StreamVoter
     * records override this to walk the body and constraint
     * expressions via {@link io.sapl.api.model.StreamOperator#evalChild}
     * and assemble the resulting {@link VoteResult}.
     * <p>
     * The default throws {@link UnsupportedOperationException} naming
     * the concrete class so the migration work list is clear at runtime
     * for any record exercised before its override is in place.
     *
     * @since 4.2.0
     */
    @Override
    default VoteResult evaluate(EvaluationContext ctx) {
        throw new UnsupportedOperationException(
                "evaluate not yet migrated for StreamVoter " + getClass().getSimpleName());
    }
}
