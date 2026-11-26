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
package io.sapl.compiler.expressions;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.model.Coverage;
import lombok.val;

@DisplayName("Repeated pure function work in one voter round")
class PureFunctionReuseTests {

    static Stream<Arguments> ordinaryConditions() {
        return Stream.of(arguments("stage.probe(subject) == 24 && stage.probe(subject) > 0", 1),
                arguments("stage.probe(subject) == 24", 1),
                arguments("stage.probe(subject) == 24 && stage.probe(action) > 0", 2),
                arguments("stage.probe(subject) == 24 || stage.probe(action) > 0", 1),
                arguments("stage.probe(subject, 2.0) == 24 && stage.probe(subject, 2.00) > 0", 2));
    }

    @MethodSource("ordinaryConditions")
    @ParameterizedTest(name = "{0}: {1} executed calls")
    @DisplayName("only an actually reached repeated computation is reused")
    void whenSimpleCallsRepeatThenOnlyTheSameReachedComputationIsReused(String condition, int expectedCalls) {
        val calls  = new ArrayList<FunctionInvocation>();
        val broker = capturingFunctionBroker(calls, ignored -> Value.of(24));
        val voter  = compilePolicy("policy \"reader\" permit " + condition + ";", compilationContext(broker));

        val result = voter.evaluate(evaluationContext(broker));

        assertThat(result.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(calls).hasSize(expectedCalls);
    }

    @Test
    @DisplayName("reusing a context for another evaluation or subscription starts fresh work")
    void whenTheVoterRunsAgainThenPreviousEvaluationValuesDoNotEscape() {
        val calls   = new ArrayList<FunctionInvocation>();
        val broker  = capturingFunctionBroker(calls, ignored -> Value.of(24));
        val voter   = compilePolicy("""
                policy "reader" permit stage.probe(subject) == 24 && stage.probe(subject) > 0;
                """, compilationContext(broker));
        val context = evaluationContext(broker);

        voter.evaluate(context);
        voter.evaluate(context);
        voter.evaluate(EvaluationContext.of("p", "c", "other", AuthorizationSubscription.of("another", "read", "desk"),
                broker));

        assertThat(calls).hasSize(3).extracting(call -> call.arguments().getFirst()).containsExactly(context.subject(),
                context.subject(), Value.of("another"));
    }

    @Test
    @DisplayName("equal current input values do not merge different argument bindings")
    void whenSubjectAndActionHaveEqualValuesThenTheirWrittenComputationsRemainDistinct() {
        val calls   = new ArrayList<FunctionInvocation>();
        val broker  = capturingFunctionBroker(calls, ignored -> Value.of(24));
        val voter   = compilePolicy("""
                policy "reader" permit stage.probe(subject) == 24 && stage.probe(action) > 0;
                """, compilationContext(broker));
        val context = EvaluationContext.of("p", "c", "s", AuthorizationSubscription.of("same", "same", "desk"), broker);

        val result = voter.evaluate(context);

        assertThat(result.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(calls).hasSize(2);
    }

    @Test
    @DisplayName("coverage still receives both reached source occurrences")
    void whenAFunctionResultIsReusedThenTheTwoConditionSitesAreStillObserved() {
        val calls    = new ArrayList<FunctionInvocation>();
        val broker   = capturingFunctionBroker(calls, ignored -> Value.of(24));
        val compiled = compilePolicyFull("""
                policy "reader" permit stage.probe(subject) == 24 && stage.probe(subject) > 0;
                """, compilationContext(broker));
        val operands = new IdentityHashMap<CompiledExpression, Boolean>();
        val context  = evaluationContext(broker)
                .withObserver((parent, operand, value) -> operands.put(operand, Boolean.TRUE));

        val result = compiled.coverageVoter().evaluate(context);

        assertThat(result.voteResult().vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(operands).hasSize(3);
        assertThat(result.coverage()).isNotNull();
        assertThat(calls).hasSize(1);
    }

    @Test
    @DisplayName("ordinary function error values are evaluated at every reached occurrence")
    void whenTheFunctionReturnsAnErrorThenItIsNotStoredForAnotherOccurrence() {
        val calls  = new ArrayList<FunctionInvocation>();
        val broker = capturingFunctionBroker(calls, ignored -> Value.error("ordinary unavailable value"));
        val voter  = compilePolicy("""
                policy "reader" permit stage.probe(subject) == 24 && stage.probe(subject) > 0;
                """, compilationContext(broker));

        val result = voter.evaluate(evaluationContext(broker));

        assertThat(result.vote().authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(calls).hasSize(2);
    }

    @Test
    @DisplayName("a normal 32768-character result is reused within the evaluation round")
    void whenTheSameCallReturnsAnOrdinaryTextThenTheResultIsComputedOnce() {
        val calls   = new ArrayList<FunctionInvocation>();
        val broker  = capturingFunctionBroker(calls,
                args -> Value.of(((TextValue) args.getFirst()).value().repeat(32_768)));
        val voter   = compilePolicy("""
                policy "reader" permit stage.payload(subject) == resource && stage.payload(subject) != action;
                """, compilationContext(broker));
        val context = EvaluationContext.of("p", "c", "s", AuthorizationSubscription.of("x", "read", "x".repeat(32_768)),
                broker);

        val result = voter.evaluate(context);

        assertThat(result.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(calls).hasSize(1);
    }

    static Stream<Arguments> boundProjections() {
        return Stream.of(arguments("subject::[stage.echo(@), stage.echo(@)]", "[[1,1],[2,2]]", 4),
                arguments("subject::[stage.echo(#), stage.echo(#)]", "[[0,0],[1,1]]", 4),
                arguments("subject::[stage.echo(action), stage.echo(action)]", """
                        [["read","read"],["read","read"]]
                        """, 1));
    }

    @MethodSource("boundProjections")
    @ParameterizedTest(name = "{0}: {2} executed calls")
    @DisplayName("relative inputs remain local while an unchanged request binding can share")
    void whenTheRelativeBinderChangesThenOnlyUnchangedArgumentsCanBeReused(String projection, String expected,
            int count) {
        val calls   = new ArrayList<FunctionInvocation>();
        val broker  = capturingFunctionBroker(calls, args -> args.getFirst());
        val voter   = compilePolicy("policy \"reader\" permit (" + projection + ") == " + expected + ";",
                compilationContext(broker));
        val context = EvaluationContext.of("p", "c", "s",
                AuthorizationSubscription.of(Value.ofArray(Value.of(1), Value.of(2)), "read", "desk"), broker);

        val result = voter.evaluate(context);

        assertThat(result.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(calls).hasSize(count);
    }

    @Test
    @DisplayName("a new snapshot round does its own pure work and retains the real attribute occurrence")
    void whenAPendingRoundIsCompletedThenItsPureResultsAreNotCarriedOver() {
        val calls   = new ArrayList<FunctionInvocation>();
        val broker  = capturingFunctionBroker(calls, ignored -> Value.of(24));
        val voter   = compilePolicy("""
                policy "reader" permit
                  stage.probe(subject) == 24 && stage.probe(subject) > 0 && <office.ready>;
                """, compilationContext(broker));
        val context = evaluationContext(broker);
        val pending = voter.evaluate(context);
        assertThat(pending.vote()).isNull();
        assertThat(pending.dependencies()).hasSize(1);
        val key = pending.dependencies().keySet().iterator().next();

        val completed = voter
                .evaluate(context.withSnapshot(Map.of(key, new AttributeSnapshot(Value.TRUE, Instant.EPOCH))));

        assertThat(completed.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(completed.dependencies()).isEqualTo(pending.dependencies());
        assertThat(calls).hasSize(2);
    }

    @Test
    @DisplayName("a shared successful value still produces type failures at both original source sites")
    void whenBothBooleanUsesHaveTheWrongTypeThenTheirErrorsKeepTheOccurrenceLocations() {
        val calls    = new ArrayList<FunctionInvocation>();
        val broker   = capturingFunctionBroker(calls, ignored -> Value.of(24));
        val source   = "policy \"reader\" permit stage.probe(subject); stage.probe(subject);";
        val compiled = compilePolicyFull(source, compilationContext(broker));
        val result   = compiled.coverageVoter().evaluate(evaluationContext(broker));
        val coverage = (Coverage.PolicyCoverage) result.coverage();

        assertThat(result.voteResult().vote().authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(coverage.bodyCoverage().hits()).hasSize(2)
                .allSatisfy(hit -> assertThat(hit.result()).isInstanceOfSatisfying(ErrorValue.class,
                        error -> assertThat(error.location()).isEqualTo(hit.location())))
                .extracting(hit -> hit.location().start())
                .containsExactly(source.indexOf("stage.probe"), source.lastIndexOf("stage.probe"));
        assertThat(result.voteResult().vote().errors()).singleElement()
                .satisfies(error -> assertThat(error.location().start()).isEqualTo(source.indexOf("stage.probe")));
        assertThat(calls).hasSize(1);
    }

    @Test
    @DisplayName("an embedded error is not retained as a reusable successful result")
    void whenAResultContainsAnErrorThenBothReachedCallsArePreserved() {
        val calls       = new ArrayList<FunctionInvocation>();
        val nestedError = Value.ofArray(Value.error("ordinary missing item"));
        val broker      = capturingFunctionBroker(calls, ignored -> nestedError);
        val voter       = compilePolicy("""
                policy "reader" permit
                transform [stage.probe(subject), stage.probe(subject)]
                """, compilationContext(broker));

        val result = voter.evaluate(evaluationContext(broker));

        assertThat(result.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        assertThat(calls).hasSize(2);
    }
}
