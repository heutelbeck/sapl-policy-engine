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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.model.Coverage.PolicySetCoverage;
import io.sapl.compiler.model.Coverage.TargetResult;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.sapl.compiler.document.DocumentCompiler.compileDocument;
import static io.sapl.compiler.expressions.ExpressionCompiler.compile;
import static io.sapl.compiler.policy.PolicyCompiler.compileConditions;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.compilePolicy;
import static io.sapl.util.SaplTesting.evaluate;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parsePolicy;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Repeated logical negation")
class LogicalNegationTests {

    private static final String NUMBER_TYPE_ERROR = "Logical operation requires boolean value, but found: 2.";

    @ParameterizedTest(name = "boolean operands of {0}")
    @ValueSource(strings = { "&&", "&", "||", "|", "^" })
    @DisplayName("Boolean operands have the same compiled structure and hash with or without a redundant guard")
    void whenABooleanOperatorChecksItsOperandsThenRedundantGuardsAreRemoved(String operator) {
        val separator = " " + operator + " ";
        val ctx       = compilationContext();
        ctx.addLocalPolicyVariable("checked", compileExpression("!!action", ctx));
        val binary  = "action" + separator + "subject";
        val ternary = String.join(separator, "resource", "action", "subject");
        assertSamePredicate(binary, "!!action" + separator + "!!subject", ctx);
        assertSamePredicate(binary, "subject" + separator + "checked", ctx);
        assertSamePredicate(binary, "subject" + separator + "(action && true)", ctx);
        assertSamePredicate(binary, "(false || action || false)" + separator + "subject", ctx);
        assertSamePredicate(ternary, String.join(separator, "!!action", "!!subject", "!!resource"), ctx);
        assertSamePredicate(ternary, String.join(separator, "checked", "subject", "resource"), ctx);
        val identity = operator.startsWith("&") ? "true" : "false";
        assertSamePredicate(identity + separator + "action", identity + separator + "!!action", ctx);
    }

    private static void assertSamePredicate(String plain, String checked, CompilationContext ctx) {
        val expected = (PureOperator) compileExpression(plain, ctx);
        val actual   = (PureOperator) compileExpression(checked, ctx);
        assertThat(new IndexPredicate(actual.semanticHash(), actual)).as(checked)
                .isEqualTo(new IndexPredicate(expected.semanticHash(), expected));
    }

    @ParameterizedTest(name = "value operands of {0}")
    @ValueSource(strings = { "==", "+" })
    @DisplayName("Guards remain significant when an operator accepts non-boolean values")
    void whenAnOperatorAcceptsOtherTypesThenACheckedOperandHasADistinctHash(String operator) {
        assertThat(hash("subject " + operator + " action")).isNotEqualTo(hash("!!subject " + operator + " !!action"));
    }

    @Test
    @DisplayName("AND reports a numeric operand at the boolean operation that checks it")
    void whenAndChecksANumberThenTheDiagnosticMarksAnd() {
        val expression = "!!subject && true";
        val value      = evaluate(expression).withSubject(Value.of(2)).value();
        assertThat(value).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertError(error, "Expected BOOLEAN but got: NumberValue.", expression));
    }

    private static long hash(String expression) {
        return ((PureOperator) compileExpression(expression)).semanticHash();
    }

    @Test
    @DisplayName("A constant error already bound to a variable propagates through XOR")
    void whenAVariableContainsAnErrorThenXorPreservesTheErrorFromItsDeclaration() {
        val ctx   = compilationContext();
        val error = compileExpression("!!2", ctx);
        ctx.addLocalPolicyVariable("invalid", error);
        assertThat(compileExpression("invalid ^ <room.enabled>", ctx)).isSameAs(error)
                .isInstanceOfSatisfying(ErrorValue.class, failure -> assertError(failure, NUMBER_TYPE_ERROR, "!2"));
    }

    @ParameterizedTest(name = "NOT checks {0} itself")
    @ValueSource(strings = { "true && <room.enabled>", "false || false || <room.enabled>",
            "true & true & <room.enabled>" })
    @DisplayName("NOT replaces a single operand identity check and keeps the attribute dependency")
    void whenNotConsumesAStreamIdentityThenNotChecksTheAttribute(String operand) {
        val expression = "!(" + operand + ")";
        val driver     = evaluate(expression);
        val pending    = driver.step();
        assertThat(pending.result()).isNull();
        val resolved = driver.with("room.enabled", Value.of(2)).step();
        assertThat(resolved).returns(pending.dependencies(), ExpressionResult::dependencies)
                .extracting(ExpressionResult::result)
                .isInstanceOfSatisfying(ErrorValue.class, error -> assertError(error, NUMBER_TYPE_ERROR, expression));
    }

    @Test
    @DisplayName("A conjunction with a folded type error retains it when negated")
    void whenOneOperandRemainsAlongsideAnErrorThenNotPreservesTheConjunction() {
        val value = evaluate("!(2 && true && subject)").withSubject(Value.TRUE).value();
        assertThat(value).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertError(error, "Expected BOOLEAN but got: NumberValue.", "2 && true && subject"));
    }

    @Test
    @DisplayName("Negating a checked variable has the identity of negating its operand")
    void whenNegatingACheckedVariableThenItsHashMatchesANegationAndItsErrorMarksTheGuard() {
        val ctx = compilationContext();
        ctx.addLocalPolicyVariable("checked", compileExpression("!!subject", ctx));
        val throughVariable = (PureOperator) compileExpression("!checked", ctx);
        val direct          = (PureOperator) compileExpression("!subject", ctx);
        val subject         = (PureOperator) compileExpression("subject", ctx);
        assertThat(throughVariable.semanticHash()).isEqualTo(direct.semanticHash())
                .isNotEqualTo(subject.semanticHash());
        assertThat(throughVariable.semanticEquals(direct)).isTrue();
        val evaluation = evaluationContext(AuthorizationSubscription.of(Value.of(2), Value.NULL, Value.NULL));
        assertThat(throughVariable.evaluate(evaluation)).isInstanceOfSatisfying(ErrorValue.class, error -> {
            assertError(error, NUMBER_TYPE_ERROR, "!subject");
            assertThat(error.location().documentSource()).isEqualTo("!!subject");
        });
    }

    @ParameterizedTest(name = "coverage marks {0}")
    @ValueSource(strings = { "!(subject < 4)", "!!subject", "!!(subject && true)" })
    @DisplayName("Target coverage identifies the entire expression written after FOR")
    void whenATargetIsNormalizedThenCoverageMarksTheWrittenTarget(String expression) {
        val source   = """
                set "access" priority permit or deny for TARGET
                policy "entry" permit
                """.replace("TARGET", expression);
        val document = compileDocument(source, compilationContext());
        val ctx      = evaluationContext(AuthorizationSubscription.of(Value.of(2), Value.NULL, Value.NULL));
        val coverage = (PolicySetCoverage) document.coverageVoter().evaluate(ctx).coverage();
        assertThat(coverage.targetHit()).isInstanceOfSatisfying(TargetResult.class,
                target -> assertThat(sourceText(target.location())).isEqualTo(expression));
    }

    @ParameterizedTest(name = "a bound {0} checks the operand once")
    @ValueSource(strings = { "!!subject", "!subject", "!!<room.enabled>", "!<room.enabled>", "subject > 0",
            "<room.level> > 0", "subject =~ action", "<room.label> =~ action", "subject ^ action",
            "<room.enabled> ^ action ^ resource", "subject && action", "<room.enabled> || action" })
    @DisplayName("An obligation reuses the boolean check across policy variable bindings")
    void whenVariablesAreAlreadyCheckedThenTheObligationReusesTheFirstCheck(String expression) {
        val policy = parsePolicy("""
                policy "checked obligation" deny
                  var a = EXPRESSION;
                  var b = !!a;
                  obligation !!b
                """.replace("EXPRESSION", expression));
        val ctx    = compilationContext();
        compileConditions(policy.body().statements(), ctx);
        assertThat(compile(policy.obligations().getFirst(), ctx)).isSameAs(ctx.getVariable("a"));
    }

    @ParameterizedTest(name = "a numeric binding of {0} still needs a boolean check")
    @ValueSource(strings = { "subject + 1", "-subject" })
    @DisplayName("Arithmetic results in a variable retain the obligation's boolean check")
    void whenAVariableContainsAnArithmeticResultThenTheObligationChecksItsType(String expression) {
        val ctx = compilationContext();
        ctx.addLocalPolicyVariable("amount", compileExpression(expression, ctx));
        val checked    = (PureOperator) compileExpression("!!amount", ctx);
        val evaluation = evaluationContext(AuthorizationSubscription.of(Value.of(1), Value.NULL, Value.NULL));
        assertThat(checked.evaluate(evaluation)).isInstanceOfSatisfying(ErrorValue.class, error -> {
            assertThat(error.message()).startsWith("Logical operation requires boolean value, but found:");
            assertThat(sourceText(error.location())).isEqualTo("!amount");
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "!(!subject) == 2;", "obligation !(!subject)" })
    @DisplayName("A number under double negation makes the policy indeterminate")
    void whenANumberIsNegatedTwiceThenThePolicyReportsTheInnerNot(String body) {
        val policy = """
                policy "access" deny
                """ + body;
        val ctx    = evaluationContext(AuthorizationSubscription.of(Value.of(2), Value.NULL, Value.NULL));
        val vote   = compilePolicy(policy).evaluate(ctx).vote();
        assertThat(vote.authorizationDecision()).satisfies(decision -> {
            assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE);
            assertThat(decision.obligations()).isEmpty();
        });
        assertThat(vote.errors()).singleElement().satisfies(error -> assertError(error, NUMBER_TYPE_ERROR, "!subject"));
    }

    @ParameterizedTest(name = "{0} on {1} yields {2}")
    @CsvSource({ "!!subject, true, true", "!!subject, false, false", "!!!subject, true, false",
            "!!!subject, false, true", "!(!(!(!subject))), false, false" })
    @DisplayName("Booleans retain the parity of the negation chain")
    void whenTheSubjectIsBooleanThenNegationHasTheExpectedPolarity(String source, boolean subject, boolean expected) {
        assertThat(evaluate(source).withSubject(Value.of(subject)).value()).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "{0} reports the error at {2}")
    @CsvSource({ "'!(!2)', '" + NUMBER_TYPE_ERROR + "', '!2'", "'!!!2', '" + NUMBER_TYPE_ERROR + "', '!2'",
            "'!!(1 / 0)', 'Division by zero.', '1 / 0'",
            "'!(!(subject < 4))', 'Numeric operation requires number values, but found: null.', 'subject < 4'" })
    @DisplayName("Negation preserves the failing operation and its location")
    void whenAnOperationFailsThenItsErrorIdentifiesTheOrigin(String source, String message, String failingSource) {
        val value = evaluate(source).withSubject(Value.NULL).value();
        assertThat(value).isInstanceOfSatisfying(ErrorValue.class, error -> assertError(error, message, failingSource));
    }

    @Test
    @DisplayName("A guarded attribute stays pending and keeps its dependency through errors and recovery")
    void whenTheAttributeChangesThenTheGuardTracksTheSameReadAcrossRounds() {
        val attribute = "room.enabled";
        val driver    = evaluate("!(!<room.enabled>)");
        val pending   = driver.step();
        assertThat(pending).returns(null, ExpressionResult::result)
                .satisfies(round -> assertThat(round.dependencies()).hasSize(1).allSatisfy((key, occurrences) -> {
                    assertThat(key.invocation().attributeName()).isEqualTo(attribute);
                    assertThat(occurrences).singleElement().satisfies(
                            occurrence -> assertThat(sourceText(occurrence.location())).isEqualTo("<room.enabled>"));
                }));

        val failure = driver.with(attribute, Value.of(2)).step();
        assertThat(failure).returns(pending.dependencies(), ExpressionResult::dependencies)
                .extracting(ExpressionResult::result).isInstanceOfSatisfying(ErrorValue.class,
                        error -> assertError(error, NUMBER_TYPE_ERROR, "!<room.enabled>"));

        val attributeError = Value.error("Sensor offline.");
        val offline        = driver.with(attribute, attributeError).step();
        assertThat(offline).returns(pending.dependencies(), ExpressionResult::dependencies)
                .extracting(ExpressionResult::result).isSameAs(attributeError);

        val recovered = driver.with(attribute, Value.FALSE).step();
        assertThat(recovered).returns(Value.FALSE, ExpressionResult::result).returns(pending.dependencies(),
                ExpressionResult::dependencies);
    }

    private static void assertError(ErrorValue error, String message, String failingSource) {
        assertThat(error).extracting(ErrorValue::message, value -> sourceText(value.location()))
                .containsExactly(message, failingSource);
    }

    private static String sourceText(SourceLocation location) {
        return location.documentSource().substring(location.start(), location.end());
    }
}
