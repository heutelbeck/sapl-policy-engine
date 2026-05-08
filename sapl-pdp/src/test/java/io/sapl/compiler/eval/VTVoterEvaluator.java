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
package io.sapl.compiler.eval;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.stream.Stream;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.Voter;
import io.sapl.util.SaplTesting;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.UUID;

/**
 * Evaluates a {@link Voter} against an {@link AttributeStore} and
 * exposes per-round votes as a {@link Stream}. A {@link Vote}
 * terminal voter or a {@link PureVoter} delivers one vote and
 * completes. A {@link StreamVoter} opens a subscription on the store,
 * re-evaluates on every callback, and pushes the resulting vote into
 * the slot when the result is non-null. Incomplete rounds (vote ==
 * null because some dep was not yet bound) are skipped.
 */
@UtilityClass
public class VTVoterEvaluator {

    public static Stream<Vote> evaluate(Voter voter, AttributeStore store) {
        return evaluate(voter, SaplTesting.evaluationContext(), store);
    }

    public static Stream<Vote> evaluate(Voter voter, EvaluationContext baseCtx, AttributeStore store) {
        val stream = new LatestSlotStream<Vote>();

        if (voter instanceof Vote v) {
            stream.put(v);
            stream.complete();
            return stream;
        }
        if (voter instanceof PureVoter p) {
            stream.put(p.vote(baseCtx));
            stream.complete();
            return stream;
        }
        if (!(voter instanceof StreamVoter streamVoter)) {
            throw new IllegalStateException("Unexpected Voter variant: " + voter.getClass().getName());
        }

        val initial = streamVoter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            if (initial.vote() != null) {
                stream.put(initial.vote());
            }
            stream.complete();
            return stream;
        }

        val sub = store.open("vt-voter-" + UUID.randomUUID(), initial.dependencies().keySet(), snap -> {
            val r = streamVoter.evaluate(baseCtx.withSnapshot(snap));
            if (r.vote() != null) {
                stream.put(r.vote());
            }
            return r.dependencies().keySet();
        });
        stream.onClose(sub::close);
        return stream;
    }
}
