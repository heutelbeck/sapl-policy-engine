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
package io.sapl.compiler.combining;

import static io.sapl.util.SaplTesting.assertStreamPathEquivalence;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compilePolicySet;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseSubscription;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.policyset.CompiledPolicySet;
import lombok.val;

/**
 * A policy body is the implicit AND of its conditions, and that AND must obey
 * Kleene strong three-valued logic even though the conditions are split into a
 * pure stratum (the index applicability predicate) and a stream stratum (the
 * attribute conditions). The operationally critical case: a stream condition
 * that resolves to FALSE makes the policy NOT_APPLICABLE and must dominate a
 * pure condition that errors, so a policy that should simply abstain cannot
 * flip an error-propagating set to INDETERMINATE.
 * <p>
 * The decision is observed through a set combined with error propagation so
 * that a per-policy INDETERMINATE surfaces as a set INDETERMINATE, and the
 * naive index is pinned as the oracle backend (the canonical and SMTDD indexes
 * must later agree with it). The truth table is asserted for both condition
 * orders because the split is by stratum, not by source position.
 */
@DisplayName("Policy body obeys Kleene AND across the pure and stream strata (naive index oracle)")
class CrossStratumKleeneTests {

    private static final String PURE_FIRST   = "subject.pure; <test.attr>;";
    private static final String STREAM_FIRST = "<test.attr>; subject.pure;";

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    @DisplayName("decision follows the Kleene truth table for both condition orders")
    void whenPureAndStreamCombineThenDecisionFollowsKleene(String label, String algorithm, String body, String pureJson,
            Value streamValue, Decision expected) {
        val compiled = compileWithNaiveIndex(algorithm, body);
        val ctx      = subscriptionWithPure(pureJson);
        assertStreamPathEquivalence(compiled, Map.of("test.attr", streamValue), ctx, expected);
    }

    private static CompiledPolicySet compileWithNaiveIndex(String algorithm, String body) {
        val ctx = compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of("NAIVE")).build());
        return compilePolicySet("""
                set "kleene"
                %s

                policy "p" permit %s
                """.formatted(algorithm, body), ctx);
    }

    private static EvaluationContext subscriptionWithPure(String pureJson) {
        return evaluationContext(parseSubscription(
                "{ \"subject\": { \"pure\": %s }, \"action\": \"read\", \"resource\": \"data\" }".formatted(pureJson)));
    }

    static Stream<Arguments> cells() {
        // pure subject.pure: true -> TRUE, false -> FALSE, 42 -> non-boolean -> ERROR
        // (runtime).
        // stream <test.attr>: published TRUE / FALSE / error value.
        // Kleene AND: FALSE dominates (including over ERROR). Else ERROR if any error.
        // Else TRUE.
        // Body TRUE -> PERMIT, FALSE -> NOT_APPLICABLE, ERROR -> INDETERMINATE.
        record Cell(String pure, String pureJson, Value stream, String streamName, Decision expected) {}
        val streamError = Value.error("stream boom");
        val table       = List.of(new Cell("pureTrue", "true", Value.TRUE, "streamTrue", Decision.PERMIT),
                new Cell("pureTrue", "true", Value.FALSE, "streamFalse", Decision.NOT_APPLICABLE),
                new Cell("pureTrue", "true", streamError, "streamError", Decision.INDETERMINATE),
                new Cell("pureFalse", "false", Value.TRUE, "streamTrue", Decision.NOT_APPLICABLE),
                new Cell("pureFalse", "false", Value.FALSE, "streamFalse", Decision.NOT_APPLICABLE),
                new Cell("pureFalse", "false", streamError, "streamError", Decision.NOT_APPLICABLE),
                new Cell("pureError", "42", Value.TRUE, "streamTrue", Decision.INDETERMINATE),
                new Cell("pureError", "42", Value.FALSE, "streamFalse", Decision.NOT_APPLICABLE),
                new Cell("pureError", "42", streamError, "streamError", Decision.INDETERMINATE));
        val orders      = List.of(arguments("pureFirst", PURE_FIRST), arguments("streamFirst", STREAM_FIRST));
        val algorithms  = List.of(arguments("priority", "priority permit or abstain errors propagate"),
                arguments("unanimous", "unanimous or abstain errors propagate"),
                arguments("unique", "unique or abstain errors propagate"));
        val args        = new ArrayList<Arguments>();
        for (val cell : table) {
            for (val order : orders) {
                val orderName = (String) order.get()[0];
                val body      = (String) order.get()[1];
                for (val algorithm : algorithms) {
                    val algorithmName   = (String) algorithm.get()[0];
                    val algorithmSource = (String) algorithm.get()[1];
                    args.add(arguments(cell.pure() + "/" + cell.streamName() + "/" + orderName + "/" + algorithmName,
                            algorithmSource, body, cell.pureJson(), cell.stream(), cell.expected()));
                }
            }
        }
        return args.stream();
    }
}
