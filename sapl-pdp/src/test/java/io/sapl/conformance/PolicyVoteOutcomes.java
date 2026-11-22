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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.subscriptionContext;

/** Complete policy votes produced by the real Java compiler, broker and evaluation loop. */
@UtilityClass
class PolicyVoteOutcomes {

    static Case withOutcome(Case source) {
        val headers   = source.expectation().lines()
                .filter(line -> line.startsWith("variables ") || line.startsWith("subscription ")).toList();
        val variables = headers.stream().filter(line -> line.startsWith("variables ")).findFirst()
                .map(line -> (ObjectValue) ValueJsonMarshaller.json(line.substring("variables ".length())))
                .orElse(Value.EMPTY_OBJECT);
        val context   = headers.stream().filter(line -> line.startsWith("subscription ")).findFirst()
                .map(line -> subscriptionContext(line.substring("subscription ".length())))
                .orElseGet(() -> evaluationContext());
        val compiler  = compilationContext(variables);
        val voter     = DocumentCompiler.compileDocument(source.document(), compiler).applicabilityAndVote();
        return outcomes(source, voter, compiler, context, headers);
    }

    static Case configurationOutcome(Case source) {
        val configuration = configuration(source);
        val evaluation    = evaluationContext();
        val context       = new CompilationContext(configuration.data(), evaluation.functionBroker());
        context.setCompilerOptions(configuration.compilerOptions());
        val voter = PdpCompiler.compilePDPConfiguration(configuration, context).voter();
        return outcomes(source, voter, context, evaluation, List.of());
    }

    static PDPConfiguration configuration(Case source) {
        val parts     = source.document().split("\n--- document ---\n");
        val documents = new LinkedHashMap<String, String>();
        for (var index = 1; index < parts.length; index++) {
            documents.put("document-" + index + ".sapl", parts[index]);
        }
        return PDPConfigurationLoader.loadFromContent(parts[0], documents, "office", "conformance");
    }

    private static Case outcomes(Case source, Voter voter, CompilationContext compiler, EvaluationContext context,
            List<String> headers) {
        // A snapshot uses the same invocation identity as the compiled policy, including configuration variables
        // and subscription secrets. Fixture expressions have their own scope for names and imports.
        val fixture = new CompilationContext(compiler.getData(), compiler.getFunctionBroker());
        val written = new ArrayList<>(headers);
        for (val input : source.expectation().lines().filter(line -> "given".equals(line) || line.startsWith("given "))
                .toList()) {
            val vote = GivenAttributes.read(input, fixture, context).first(
                    snapshot -> voter.evaluate(context.withSnapshot(snapshot)), VoteResult::vote,
                    round -> round.dependencies().keySet());
            written.add(input);
            written.add("decision " + encode(vote.authorizationDecision()));
        }
        for (val input : source.expectation().lines()
                .filter(line -> "snapshot".equals(line) || line.startsWith("snapshot ")).toList()) {
            val result = voter
                    .evaluate(context.withSnapshot(GivenAttributes.read(input, fixture, context).observations()));
            written.add(input);
            val vote = result.vote();
            if (vote == null) {
                written.add("vote pending");
            } else {
                written.add("vote " + encode(vote.authorizationDecision()));
            }
            written.add(GivenAttributes.reads(result.dependencies()));
        }
        return source.withExpectation(String.join("\n", written));
    }

    static String encode(AuthorizationDecision decision) {
        val fields = ObjectValue.builder();
        fields.put("decision", Value.of(decision.decision().name()));
        fields.put("obligations", decision.obligations());
        fields.put("advice", decision.advice());
        if (decision.resource() != Value.UNDEFINED) {
            fields.put("resource", decision.resource());
        }
        return ValueJsonMarshaller.toJsonString(fields.build());
    }
}
