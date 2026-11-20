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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseExpression;

/**
 * The outcomes of the evaluation corpus: what this engine yields for an
 * expression and the inputs of a case, written in the format of the corpus.
 * <p>
 * A case whose outcome line starts with {@code java:} states the outcome all
 * engines agree on and, on that line, what this engine yields instead. A
 * stated outcome stays as written if its value, error or refusal agrees.
 * Strata and messages are information only.
 */
@UtilityClass
public class EvaluationOutcomes {

    private static final String ACTION           = "action";
    private static final String COMPILER_OPTIONS = "compilerOptions";
    private static final String ENVIRONMENT      = "environment";
    private static final String RESOURCE         = "resource";
    private static final String SUBJECT          = "subject";

    private static final String CONSTANT        = "constant";
    private static final String ERROR           = " error: ";
    private static final String JAVA            = "java: ";
    private static final String LINE_FEED       = "\n";
    private static final String NAME_AND_VALUE  = ": ";
    private static final String PURE            = "pure";
    private static final String REFUSED         = "refused: ";
    private static final String STREAMING       = "streaming";
    private static final String UNDEFINED       = ": undefined";
    private static final String UNREPRESENTABLE = ": UNREPRESENTABLE ";

    private static final List<String> INPUTS   = List.of(SUBJECT, ACTION, RESOURCE, ENVIRONMENT, COMPILER_OPTIONS);
    private static final List<String> OUTCOMES = List.of(CONSTANT, PURE, STREAMING, REFUSED);

    /**
     * @param evaluationCase a case of the evaluation corpus
     * @return the case with the outcome of this engine: as its outcome, or on
     * the line {@code java:} if the case has one
     */
    public static Case withOutcome(Case evaluationCase) {
        val lines   = evaluationCase.expectation().lines().toList();
        val inputs  = lines.stream().filter(EvaluationOutcomes::isInput).toList();
        val stated  = lines.stream().filter(line -> OUTCOMES.stream().anyMatch(line::startsWith)).toList();
        val java    = lines.stream().filter(line -> line.startsWith(JAVA)).findFirst();
        val outcome = outcomeOf(evaluationCase.document(), inputs);
        val written = new ArrayList<>(inputs);
        if (java.isPresent()) {
            written.addAll(stated);
            written.add(JAVA + asStated(outcome, java.get().substring(JAVA.length())));
        } else {
            written.add(stated.isEmpty() ? outcome : asStated(outcome, stated.getFirst()));
        }
        return evaluationCase.withExpectation(String.join(LINE_FEED, written));
    }

    /**
     * @param expression a SAPL expression
     * @param inputs the input lines of a case, such as {@code subject: "a"}
     * @return what this engine yields for the expression and the inputs, as an
     * outcome line of the corpus
     */
    public static String outcomeOf(String expression, List<String> inputs) {
        val given = new HashMap<String, Value>();
        for (val line : inputs) {
            val separator = line.indexOf(NAME_AND_VALUE);
            given.put(line.substring(0, separator),
                    ValueJsonMarshaller.json(line.substring(separator + NAME_AND_VALUE.length())));
        }
        val context = compilationContext();
        if (given.get(COMPILER_OPTIONS) instanceof ObjectValue options) {
            context.setCompilerOptions(options);
        }
        final CompiledExpression compiled;
        try {
            compiled = ExpressionCompiler.compile(parseExpression(expression), context);
        } catch (SaplCompilerException refusal) {
            return REFUSED + oneLine(refusal.getMessage());
        }
        return switch (compiled) {
        case Value value               -> CONSTANT + written(value);
        case PureOperator pureOperator ->
            PURE + written(pureOperator.evaluate(evaluationContext(new AuthorizationSubscription(input(given, SUBJECT),
                    input(given, ACTION), input(given, RESOURCE), input(given, ENVIRONMENT), Value.EMPTY_OBJECT))));
        case StreamOperator ignored    -> STREAMING;
        };
    }

    private static boolean isInput(String line) {
        return INPUTS.stream().anyMatch(input -> line.startsWith(input + NAME_AND_VALUE));
    }

    private static String asStated(String outcome, String stated) {
        if (agrees(outcome, stated)) {
            return stated;
        }
        return outcome;
    }

    private static boolean agrees(String outcome, String stated) {
        if (outcome.startsWith(REFUSED) || stated.startsWith(REFUSED)) {
            return outcome.startsWith(REFUSED) && stated.startsWith(REFUSED);
        }
        val returned = withoutStratum(outcome);
        val expected = withoutStratum(stated);
        if (returned.startsWith(ERROR) || expected.startsWith(ERROR)) {
            return returned.startsWith(ERROR) && expected.startsWith(ERROR);
        }
        if (!returned.startsWith(NAME_AND_VALUE) || !expected.startsWith(NAME_AND_VALUE)
                || returned.startsWith(UNREPRESENTABLE) || expected.startsWith(UNREPRESENTABLE)) {
            return false;
        }
        return FunctionOutcomes.valueOf(returned.substring(NAME_AND_VALUE.length()))
                .equals(FunctionOutcomes.valueOf(expected.substring(NAME_AND_VALUE.length())));
    }

    private static String withoutStratum(String outcome) {
        for (val stratum : List.of(CONSTANT, PURE, STREAMING)) {
            if (outcome.startsWith(stratum + NAME_AND_VALUE) || outcome.startsWith(stratum + ERROR)) {
                return outcome.substring(stratum.length());
            }
        }
        return outcome;
    }

    private static Value input(Map<String, Value> given, String name) {
        return given.getOrDefault(name, Value.UNDEFINED);
    }

    private static String written(Value value) {
        return switch (value) {
        case ErrorValue error       -> ERROR + oneLine(error.message());
        case UndefinedValue ignored -> UNDEFINED;
        default                     -> {
            try {
                yield NAME_AND_VALUE + ValueJsonMarshaller.toJsonString(value);
            } catch (IllegalArgumentException notJson) {
                yield UNREPRESENTABLE + value;
            }
        }
        };
    }

    private static String oneLine(String message) {
        return message.replace('\n', ' ');
    }
}
