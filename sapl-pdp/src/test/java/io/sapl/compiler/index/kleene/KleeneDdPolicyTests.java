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
package io.sapl.compiler.index.kleene;

import static io.sapl.compiler.index.IndexTestFixtures.stubDocumentWithConstantApplicability;
import static io.sapl.util.SaplTesting.compilePolicyFull;
import static io.sapl.util.SaplTesting.subscriptionContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.PolicyMatches;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.compiler.index.kleene.KleeneDdIndex.Matches;
import io.sapl.compiler.index.naive.NaivePolicyIndex;
import io.sapl.compiler.index.smtdd.SmtddPolicyIndex;
import lombok.val;

@DisplayName("The reference diagram classifies compiled SAPL applicability")
class KleeneDdPolicyTests {

    private static final int NODE_LIMIT = 100_000;

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    @DisplayName("nested conditions and repeated role constraints agree with direct policy evaluation")
    void whenCompiledPoliciesContainConjunctionsAndErrorsThenReferenceMatchesNaive(String scenario, String subject) {
        val documents = policies("""
                policy "doctor" permit subject.role == "doctor"
                """, """
                policy "nurse" permit subject.role == "nurse"
                """, """
                policy "contradictoryRole" permit
                    subject.role == "doctor";
                    subject.role == "nurse"
                """, """
                policy "overlappingMembership" permit
                    subject.role in ["doctor", "nurse"];
                    subject.role in ["nurse", "admin"]
                """, """
                policy "dominatedOr" permit (10 / subject.n > 0) || subject.flag
                """, """
                policy "dominatedAnd" permit (10 / subject.n > 0); subject.flag
                """, """
                policy "complement" permit (10 / subject.n > 0) || !(10 / subject.n > 0)
                """, """
                policy "negated" permit !(subject.flag || subject.role == "doctor")
                """, """
                policy "doctorAgain" deny subject.role == "doctor"
                """);
        val context   = subscriptionContext(request(subject));
        val expected  = classify(documents, NaivePolicyIndex.create(documents).matchKleene(context));
        val reference = CompiledKleeneDdIndex.create(documents, NODE_LIMIT);
        assertThat(reference.lookup(context)).as(scenario).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    @DisplayName("the reference and current backends agree on shared role predicates and error-dominating alternatives")
    void whenComparedOnASharedCorpusThenAllIndexesReturnTheSameClassifications(String scenario, String subject) {
        val documents = policies("""
                policy "doctor" permit subject.role == "doctor"
                """, """
                policy "nurse" permit subject.role == "nurse"
                """, """
                policy "alternative" permit subject.flag || (10 / subject.n > 0)
                """);
        val context   = subscriptionContext(request(subject));
        val expected  = classify(documents, NaivePolicyIndex.create(documents).matchKleene(context));
        assertThat(CompiledKleeneDdIndex.create(documents, NODE_LIMIT).lookup(context)).as("plain DD: %s", scenario)
                .isEqualTo(expected);
        assertThat(classify(documents, CanonicalPolicyIndex.create(documents).matchKleene(context)))
                .as("count-and-eliminate: %s", scenario).isEqualTo(expected);
        assertThat(classify(documents, SmtddPolicyIndex.create(documents, NODE_LIMIT).matchKleene(context)))
                .as("SMTDD: %s", scenario).isEqualTo(expected);
    }

    @Test
    @DisplayName("constant applicability preserves true, false, and error classifications")
    void whenCompiledApplicabilityConstantThenNoPredicateIsNeeded() {
        val documents = List.of(stubDocumentWithConstantApplicability("always", Value.TRUE),
                stubDocumentWithConstantApplicability("never", Value.FALSE),
                stubDocumentWithConstantApplicability("broken", new ErrorValue("Missing configuration.")));
        val reference = CompiledKleeneDdIndex.create(documents, NODE_LIMIT);
        val context   = subscriptionContext(request("{}"));
        assertThat(reference.predicates()).isEmpty();
        assertThat(reference.lookup(context)).isEqualTo(new Matches(Set.of(0), Set.of(2)))
                .isEqualTo(classify(documents, NaivePolicyIndex.create(documents).matchKleene(context)));
    }

    private static Stream<Arguments> requests() {
        return Stream.of(arguments("doctor with a valid numeric condition", """
                {"role":"doctor","n":2,"flag":true}
                """), arguments("nurse with a false numeric condition", """
                {"role":"nurse","n":-2,"flag":false}
                """), arguments("doctor with an error dominated by true", """
                {"role":"doctor","n":0,"flag":true}
                """), arguments("nurse with an error dominated by false", """
                {"role":"nurse","n":0,"flag":false}
                """), arguments("admin outside the intersecting role lists", """
                {"role":"admin","n":2,"flag":false}
                """), arguments("unknown role and non-Boolean flag", """
                {"role":"visitor","n":0,"flag":"invalid"}
                """), arguments("missing attributes", "{}"), arguments("attributes have incompatible types", """
                {"role":17,"n":"invalid","flag":null}
                """));
    }

    private static List<CompiledDocument> policies(String... sources) {
        return Stream.of(sources).map(source -> (CompiledDocument) compilePolicyFull(source)).toList();
    }

    private static String request(String subject) {
        return """
                {"subject":%s,"action":"read","resource":"record"}
                """.formatted(subject);
    }

    private static Matches classify(List<CompiledDocument> documents, PolicyMatches result) {
        val identities = new IdentityHashMap<CompiledDocument, Integer>();
        for (var identity = 0; identity < documents.size(); identity++) {
            identities.put(documents.get(identity), identity);
        }
        val matched = new HashSet<Integer>();
        val errored = new HashSet<Integer>();
        result.trueMatches().forEach(document -> matched.add(identities.get(document)));
        result.errorMatches().forEach(error -> errored.add(identities.get(error.document())));
        return new Matches(matched, errored);
    }
}
