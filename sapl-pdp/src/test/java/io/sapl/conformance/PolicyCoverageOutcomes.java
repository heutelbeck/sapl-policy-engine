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
package io.sapl.conformance;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.model.Coverage;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;

/** Authored condition hits in a fixed round and the first complete round of a live policy. */
@UtilityClass
class PolicyCoverageOutcomes {

    static Case withOutcome(Case source) {
        val written = new ArrayList<String>();
        val inputs  = source.expectation().lines().filter(line -> "given".equals(line) || line.startsWith("given "))
                .toList();
        for (val input : inputs) {
            written.add(input);
            collect(source.document(), GivenAttributes.read(input), false, written);
            collect(source.document(), GivenAttributes.read(input), true, written);
        }
        return source.withExpectation(String.join("\n", written));
    }

    private static void collect(String document, GivenAttributes given, boolean eager, List<String> written) {
        val context = compilationContext();
        val options = ObjectValue.builder().put("lowLatencyMode", Value.of(eager)).build();
        context.setCompilerOptions(options);
        val voter  = DocumentCompiler.compileDocument(document, context).coverageVoter();
        val inputs = evaluationContext();
        val fixed  = voter.evaluate(inputs.withSnapshot(given.observations()));
        val mode   = eager ? "eager" : "lazy";
        outcome(mode, fixed, written);
        written.add(GivenAttributes.reads(fixed.voteResult().dependencies()));
        val first = given.first(snapshot -> voter.evaluate(inputs.withSnapshot(snapshot)),
                result -> result.voteResult().vote() == null ? null : result,
                result -> result.voteResult().dependencies().keySet());
        outcome("first", first, written);
    }

    private static void outcome(String prefix, VoteResultWithCoverage result, List<String> written) {
        val vote     = result.voteResult().vote();
        val decision = vote == null ? "pending" : PolicyVoteOutcomes.encode(vote.authorizationDecision());
        written.add(prefix + " " + decision);
        val body = ((Coverage.PolicyCoverage) result.coverage()).bodyCoverage();
        val hits = body.hits().stream().map(PolicyCoverageOutcomes::hit).collect(Collectors.joining(" "));
        written.add(("hits " + body.numberOfConditions() + " " + hits).stripTrailing());
    }

    private static String hit(Coverage.ConditionHit hit) {
        val value = hit.result() instanceof ErrorValue ? "error" : ValueJsonMarshaller.toJsonString(hit.result());
        return hit.statementId() + "@" + hit.location().start() + ":" + hit.location().end() + "=" + value;
    }
}
