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
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

import static io.sapl.util.SaplTesting.evaluationContext;

/** The reached policy conditions and set targets of an entire configuration, both fixed and live. */
@UtilityClass
class ConfigurationCoverageOutcomes {

    static Case withOutcome(Case source) {
        val configuration = PolicyVoteOutcomes.configuration(source);
        val inputs        = source.expectation().lines()
                .filter(line -> "given".equals(line) || line.startsWith("given ")).distinct().toList();
        val written       = new ArrayList<String>();
        for (val eager : List.of(false, true)) {
            collect(configuration, eager, inputs, written);
        }
        return source.withExpectation(String.join("\n", written));
    }

    private static void collect(PDPConfiguration configuration, boolean eager, List<String> inputs,
            List<String> written) {
        val evaluation = evaluationContext();
        val compiler   = new CompilationContext(configuration.data(), evaluation.functionBroker());
        compiler.setCompilerOptions(ObjectValue.builder().putAll(configuration.compilerOptions())
                .put("lowLatencyMode", Value.of(eager)).build());
        val voter    = PdpCompiler.compilePDPConfiguration(configuration, compiler).coverageVoter();
        val fixtures = new CompilationContext(configuration.data(), evaluation.functionBroker());
        written.add(eager ? "eager" : "lazy");
        for (val input : inputs) {
            written.add(input);
            val given = GivenAttributes.read(input, fixtures, evaluation);
            val fixed = voter.evaluate(evaluation.withSnapshot(given.observations()));
            outcome("fixed", fixed, written);
            written.add(GivenAttributes.reads(fixed.voteResult().dependencies()));
            val first = given.first(snapshot -> voter.evaluate(evaluation.withSnapshot(snapshot)),
                    result -> result.voteResult().vote() == null ? null : result,
                    result -> result.voteResult().dependencies().keySet());
            outcome("first", first, written);
        }
    }

    private static void outcome(String phase, VoteResultWithCoverage result, List<String> written) {
        val vote     = result.voteResult().vote();
        val decision = vote == null ? "pending" : PolicyVoteOutcomes.encode(vote.authorizationDecision());
        written.add(phase + " " + decision);
        written.add("coverage " + ValueJsonMarshaller.toJsonString(trace(result.coverage())));
    }

    private static Value trace(Coverage.DocumentCoverage document) {
        return switch (document) {
        case Coverage.PdpCoverage pdp       ->
            Value.ofArray(pdp.documentCoverages().stream().map(ConfigurationCoverageOutcomes::trace).toList());
        case Coverage.PolicyCoverage policy -> Value.ofArray(Value.of("policy"), Value.of(policy.voter().name()),
                Value.of(policy.bodyCoverage().numberOfConditions()),
                Value.ofArray(policy.bodyCoverage().hits().stream().map(ConfigurationCoverageOutcomes::hit).toList()));
        case Coverage.PolicySetCoverage set ->
            Value.ofArray(Value.of("set"), Value.of(set.voter().name()), target(set.targetHit()),
                    Value.ofArray(set.policyCoverages().stream().map(ConfigurationCoverageOutcomes::trace).toList()));
        };
    }

    private static Value target(Coverage.TargetHit hit) {
        return switch (hit) {
        case Coverage.BlankTargetHit ignored -> Value.NULL;
        case Coverage.NoTargetHit ignored    -> Value.of("unreached");
        case Coverage.TargetResult target    -> Value.ofArray(Value.of(target.location().start()),
                Value.of(target.location().end()), observed(target.match()));
        };
    }

    private static Value hit(Coverage.ConditionHit hit) {
        return Value.ofArray(Value.of(hit.statementId()), Value.of(hit.location().start()),
                Value.of(hit.location().end()), observed(hit.result()));
    }

    private static Value observed(Value value) {
        return value instanceof ErrorValue ? Value.of("error") : value;
    }
}
