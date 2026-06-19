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
package io.sapl.compiler.policy;

import static io.sapl.util.SaplTesting.compilePolicyFull;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.pdp.Decision;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.api.TestAttributeBroker;
import io.sapl.util.VoterEvaluator;
import lombok.val;

/**
 * A policy's applicability-and-vote voter combines the pure body stratum
 * (applicability) with the streaming body stratum under Kleene strong
 * three-valued AND. This is the voter the {@code first} combining algorithm and
 * policy-set policy walking evaluate directly, so a stream condition resolving
 * to FALSE must dominate a pure condition that errors and make the policy
 * NOT_APPLICABLE rather than INDETERMINATE. Asserted for both condition orders
 * because the strata split is by kind, not by source position.
 */
@DisplayName("Per-policy applicability-and-vote obeys Kleene AND across the pure and stream strata")
class PolicyCrossStratumKleeneTests {

    private static final String PURE_FIRST   = "subject.pure; <test.attr>;";
    private static final String STREAM_FIRST = "<test.attr>; subject.pure;";

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    @DisplayName("decision follows the Kleene truth table for both condition orders")
    void whenPureAndStreamCombineThenPolicyDecisionFollowsKleene(String label, String body, String pureJson,
            Value streamValue, Decision expected) throws InterruptedException {
        val voter = compilePolicyFull("policy \"p\" permit " + body).applicabilityAndVote();
        val ctx   = evaluationContext(parseSubscription(
                "{ \"subject\": { \"pure\": %s }, \"action\": \"read\", \"resource\": \"data\" }".formatted(pureJson)));
        try (val broker = new TestAttributeBroker()) {
            broker.register("test.attr", streamValue);
            try (val stream = VoterEvaluator.evaluate(voter, ctx, broker)) {
                assertThat(stream.awaitNext().authorizationDecision().decision()).isEqualTo(expected);
            }
        }
    }

    @ParameterizedTest(name = "stream {1} -> {2}")
    @MethodSource("constantPureErrorCells")
    @DisplayName("a constant-folding pure error still combines with the streaming section under Kleene")
    void whenConstantPureErrorThenStreamingSectionStillDominates(Value streamValue, String streamName,
            Decision expected) throws InterruptedException {
        // "42" is a non-boolean constant, so the pure section constant-folds to an
        // error.
        val voter = compilePolicyFull("policy \"p\" permit 42; <test.attr>;").applicabilityAndVote();
        val ctx   = evaluationContext(parseSubscription("""
                { "subject": "alice", "action": "read", "resource": "data" }
                """));
        try (val broker = new TestAttributeBroker()) {
            broker.register("test.attr", streamValue);
            try (val stream = VoterEvaluator.evaluate(voter, ctx, broker)) {
                assertThat(stream.awaitNext().authorizationDecision().decision()).isEqualTo(expected);
            }
        }
    }

    static Stream<Arguments> constantPureErrorCells() {
        return Stream.of(arguments(Value.FALSE, "false", Decision.NOT_APPLICABLE),
                arguments(Value.TRUE, "true", Decision.INDETERMINATE),
                arguments(Value.error("stream boom"), "error", Decision.INDETERMINATE));
    }

    static Stream<Arguments> cells() {
        // pure subject.pure: true -> TRUE, false -> FALSE, 42 -> non-boolean -> ERROR
        // (runtime). stream <test.attr>: TRUE / FALSE / error value.
        // Kleene AND: FALSE dominates (including over ERROR); else ERROR if any error;
        // else TRUE. Body TRUE -> PERMIT, FALSE -> NOT_APPLICABLE, ERROR ->
        // INDETERMINATE.
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
        val args        = new ArrayList<Arguments>();
        for (val cell : table) {
            for (val order : orders) {
                val orderName = (String) order.get()[0];
                val body      = (String) order.get()[1];
                args.add(arguments(cell.pure() + "/" + cell.streamName() + "/" + orderName, body, cell.pureJson(),
                        cell.stream(), cell.expected()));
            }
        }
        return args.stream();
    }
}
