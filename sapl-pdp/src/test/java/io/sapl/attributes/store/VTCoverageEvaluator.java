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
package io.sapl.attributes.store;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.stream.QueueStream;
import io.sapl.api.stream.Stream;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.policy.CoverageVoter;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.UUID;

/**
 * Evaluates a {@link CoverageVoter} against an {@link AttributeStore}
 * and exposes per-round results as an ordered, full-fidelity
 * {@link Stream} of {@link VoteResultWithCoverage}. Every round whose
 * vote resolves is delivered to the consumer; intermediate rounds
 * are not dropped. If the initial evaluation returns no dependencies
 * (pure policy body), the stream delivers one result and completes.
 * Otherwise it opens a subscription on the store and re-evaluates
 * the voter on every callback.
 */
@UtilityClass
public class VTCoverageEvaluator {

    public static Stream<VoteResultWithCoverage> evaluate(CoverageVoter voter, AttributeStore store) {
        return evaluate(voter, SaplTesting.evaluationContext(), store);
    }

    public static Stream<VoteResultWithCoverage> evaluate(CoverageVoter voter, EvaluationContext baseCtx,
            AttributeStore store) {
        val stream = new QueueStream<VoteResultWithCoverage>();

        val initial = voter.evaluate(baseCtx);
        if (initial.voteResult().dependencies().isEmpty()) {
            stream.put(initial);
            stream.complete();
            return stream;
        }

        val sub = store.open("vt-cov-" + UUID.randomUUID(), initial.voteResult().dependencies().keySet(), snap -> {
            val r = voter.evaluate(baseCtx.withSnapshot(snap));
            // Skip on incomplete: a null vote means the body did not resolve
            // (some dep still missing); do not surface a partial coverage
            // result to the consumer.
            if (r.voteResult().vote() != null) {
                stream.put(r);
            }
            return r.voteResult().dependencies().keySet();
        });
        stream.onClose(sub::close);
        return stream;
    }
}
