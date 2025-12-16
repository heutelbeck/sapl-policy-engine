/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.grammar.antlr.validation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.antlr.v4.runtime.tree.ParseTreeWalker;

import io.sapl.test.grammar.antlr.SAPLTestParser.DurationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ExpectOrAdjustBlockContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.GivenContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.MultipleAmountContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.RepeatedExpectationContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.RequirementContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.ScenarioContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.StringMatchesRegexContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.StringWithLengthContext;
import io.sapl.test.grammar.antlr.SAPLTestParser.VirtualTimeMockContext;
import io.sapl.test.grammar.antlr.SAPLTestParserBaseListener;

/**
 * Semantic validator for SAPLTest documents.
 * Validates constraints that cannot be expressed in the grammar alone.
 */
public class SAPLTestValidator {

    public static final String MSG_INVALID_JAVA_DURATION                       = "Duration is not a valid Java Duration.";
    public static final String MSG_JAVA_DURATION_ZERO_OR_NEGATIVE              = "Duration needs to be larger than 0.";
    public static final String MSG_INVALID_MULTIPLE_AMOUNT                     = "Amount needs to be a natural number larger than 1.";
    public static final String MSG_GIVEN_WITH_MORE_THAN_ONE_VIRTUAL_TIME       = "TestCase contains more than one virtual-time declaration.";
    public static final String MSG_STRING_MATCHES_REGEX_WITH_INVALID_REGEX     = "The given regex has an invalid format.";
    public static final String MSG_INVALID_STRING_WITH_LENGTH                  = "String length needs to be a natural number larger than 0.";
    public static final String MSG_DUPLICATE_REQUIREMENT_NAME                  = "Requirement name must be unique.";
    public static final String MSG_DUPLICATE_SCENARIO_NAME                     = "Scenario name must be unique within a requirement.";
    public static final String MSG_NON_ALTERNATING_EXPECT_OR_ADJUSTMENT_BLOCKS = "Then and expect blocks must alternate.";
    public static final String MSG_INVALID_REPEATED_EXPECT                     = "Repeated expectation must end with an expect block.";

    /**
     * Validates a SAPLTest document parse tree.
     *
     * @param saplTest the root context of the parsed SAPLTest document
     * @return list of validation errors (empty if document is valid)
     */
    public List<ValidationError> validate(SaplTestContext saplTest) {
        if (saplTest == null) {
            return List.of();
        }

        var errors = new ArrayList<ValidationError>();

        validateUniqueRequirementNames(saplTest, errors::add);

        for (var requirement : saplTest.requirement()) {
            validateRequirement(requirement, errors::add);
        }

        return errors;
    }

    private void validateUniqueRequirementNames(SaplTestContext saplTest, Consumer<ValidationError> errorConsumer) {
        var names = new HashSet<String>();
        for (var requirement : saplTest.requirement()) {
            var name = unquote(requirement.name.getText());
            if (!names.add(name)) {
                errorConsumer.accept(ValidationError.fromToken(MSG_DUPLICATE_REQUIREMENT_NAME, requirement.name));
            }
        }
    }

    private void validateRequirement(RequirementContext requirement, Consumer<ValidationError> errorConsumer) {
        validateUniqueScenarioNames(requirement, errorConsumer);

        if (requirement.given() != null) {
            validateGiven(requirement.given(), errorConsumer);
        }

        for (var scenario : requirement.scenario()) {
            validateScenario(scenario, errorConsumer);
        }

        walkRequirementForValueValidations(requirement, errorConsumer);
    }

    private void walkRequirementForValueValidations(RequirementContext requirement,
            Consumer<ValidationError> errorConsumer) {
        ParseTreeWalker.DEFAULT.walk(new SAPLTestParserBaseListener() {
            @Override
            public void enterDuration(DurationContext context) {
                validateDuration(context, errorConsumer);
            }

            @Override
            public void enterMultipleAmount(MultipleAmountContext context) {
                validateMultipleAmount(context, errorConsumer);
            }

            @Override
            public void enterStringMatchesRegex(StringMatchesRegexContext context) {
                validateRegex(context, errorConsumer);
            }

            @Override
            public void enterStringWithLength(StringWithLengthContext context) {
                validateStringLength(context, errorConsumer);
            }
        }, requirement);
    }

    private void validateUniqueScenarioNames(RequirementContext requirement, Consumer<ValidationError> errorConsumer) {
        var names = new HashSet<String>();
        for (var scenario : requirement.scenario()) {
            var name = unquote(scenario.name.getText());
            if (!names.add(name)) {
                errorConsumer.accept(ValidationError.fromToken(MSG_DUPLICATE_SCENARIO_NAME, scenario.name));
            }
        }
    }

    private void validateScenario(ScenarioContext scenario, Consumer<ValidationError> errorConsumer) {
        if (scenario.given() != null) {
            validateGiven(scenario.given(), errorConsumer);
        }

        validateExpectation(scenario.expectation(), errorConsumer);
    }

    private void validateGiven(GivenContext given, Consumer<ValidationError> errorConsumer) {
        var                    virtualTimeCount = 0L;
        VirtualTimeMockContext firstVirtualTime = null;

        for (var givenStep : given.givenStep()) {
            if (givenStep instanceof io.sapl.test.grammar.antlr.SAPLTestParser.MockGivenStepContext mockStep) {
                if (mockStep.mockDefinition() instanceof VirtualTimeMockContext vtContext) {
                    virtualTimeCount++;
                    if (firstVirtualTime == null) {
                        firstVirtualTime = vtContext;
                    }
                }
            }
        }

        if (virtualTimeCount > 1) {
            errorConsumer
                    .accept(ValidationError.fromContext(MSG_GIVEN_WITH_MORE_THAN_ONE_VIRTUAL_TIME, firstVirtualTime));
        }
    }

    private void validateExpectation(io.sapl.test.grammar.antlr.SAPLTestParser.ExpectationContext expectation,
            Consumer<ValidationError> errorConsumer) {
        if (expectation instanceof RepeatedExpectationContext repeated) {
            validateRepeatedExpectation(repeated, errorConsumer);
        }
    }

    private void validateRepeatedExpectation(RepeatedExpectationContext repeated,
            Consumer<ValidationError> errorConsumer) {
        var blocks = repeated.expectOrAdjustBlock();
        if (blocks.isEmpty()) {
            return;
        }

        ExpectOrAdjustBlockContext previous = null;
        for (var block : blocks) {
            if (previous != null && isSameBlockType(previous, block)) {
                errorConsumer
                        .accept(ValidationError.fromContext(MSG_NON_ALTERNATING_EXPECT_OR_ADJUSTMENT_BLOCKS, block));
                return;
            }
            previous = block;
        }

        var lastBlock = blocks.getLast();
        if (lastBlock instanceof io.sapl.test.grammar.antlr.SAPLTestParser.AdjustBlockElementContext) {
            errorConsumer.accept(ValidationError.fromContext(MSG_INVALID_REPEATED_EXPECT, lastBlock));
        }
    }

    private boolean isSameBlockType(ExpectOrAdjustBlockContext first, ExpectOrAdjustBlockContext second) {
        var firstIsExpect  = first instanceof io.sapl.test.grammar.antlr.SAPLTestParser.ExpectBlockElementContext;
        var secondIsExpect = second instanceof io.sapl.test.grammar.antlr.SAPLTestParser.ExpectBlockElementContext;
        return firstIsExpect == secondIsExpect;
    }

    private void validateDuration(DurationContext context, Consumer<ValidationError> errorConsumer) {
        var durationString = unquote(context.value.getText());
        try {
            var parsedDuration = Duration.parse(durationString);
            if (parsedDuration.isZero() || parsedDuration.isNegative()) {
                errorConsumer.accept(ValidationError.fromToken(MSG_JAVA_DURATION_ZERO_OR_NEGATIVE, context.value));
            }
        } catch (DateTimeParseException e) {
            errorConsumer.accept(ValidationError.fromToken(MSG_INVALID_JAVA_DURATION, context.value));
        }
    }

    private void validateMultipleAmount(MultipleAmountContext context, Consumer<ValidationError> errorConsumer) {
        try {
            var amount = new BigDecimal(context.amount.getText()).intValueExact();
            if (amount < 2) {
                errorConsumer.accept(ValidationError.fromToken(MSG_INVALID_MULTIPLE_AMOUNT, context.amount));
            }
        } catch (ArithmeticException | NumberFormatException e) {
            errorConsumer.accept(ValidationError.fromToken(MSG_INVALID_MULTIPLE_AMOUNT, context.amount));
        }
    }

    private void validateRegex(StringMatchesRegexContext context, Consumer<ValidationError> errorConsumer) {
        var regex = unquote(context.regex.getText());
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            errorConsumer.accept(ValidationError.fromToken(MSG_STRING_MATCHES_REGEX_WITH_INVALID_REGEX, context.regex));
        }
    }

    private void validateStringLength(StringWithLengthContext context, Consumer<ValidationError> errorConsumer) {
        try {
            var length = new BigDecimal(context.length.getText()).intValueExact();
            if (length < 1) {
                errorConsumer.accept(ValidationError.fromToken(MSG_INVALID_STRING_WITH_LENGTH, context.length));
            }
        } catch (ArithmeticException | NumberFormatException e) {
            errorConsumer.accept(ValidationError.fromToken(MSG_INVALID_STRING_WITH_LENGTH, context.length));
        }
    }

    private String unquote(String text) {
        if (text != null && text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

}
