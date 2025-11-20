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
package io.sapl.compiler;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for evaluating filter function arguments across different
 * expression natures (VALUE, PURE, STREAM).
 */
@UtilityClass
class FilterArgumentEvaluator {

    private static final String ERROR_STREAM_IN_PURE_FILTER_ARGS = "Stream expression in pure filter arguments. Should not be possible.";

    /**
     * Extracts value arguments for constant folding (Nature.VALUE).
     * <p>
     * All arguments are already Values, just need to prepend the left-hand
     * argument.
     *
     * @param arguments the compiled arguments
     * @param leftHandArg the left-hand argument (target value)
     * @return list of Value arguments, or ErrorValue if any argument is an error
     */
    static List<Value> extractValueArguments(CompiledArguments arguments, Value leftHandArg) {
        val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
        valueArguments.add(leftHandArg);

        for (val arg : arguments.arguments()) {
            val argValue = (Value) arg;
            if (argValue instanceof ErrorValue) {
                return List.of(argValue);
            }
            valueArguments.add(argValue);
        }

        return valueArguments;
    }

    /**
     * Evaluates pure arguments at runtime (Nature.PURE).
     * <p>
     * Arguments may be Values or PureExpressions that need runtime evaluation.
     *
     * @param arguments the compiled arguments
     * @param leftHandArg the left-hand argument (target value)
     * @param ctx the evaluation context
     * @return list of evaluated Value arguments
     */
    static List<Value> evaluatePureArguments(CompiledArguments arguments, Value leftHandArg,
            io.sapl.api.model.EvaluationContext ctx) {
        val valueArguments = new ArrayList<Value>(arguments.arguments().length + 1);
        valueArguments.add(leftHandArg);

        for (val argument : arguments.arguments()) {
            switch (argument) {
            case Value value                   -> valueArguments.add(value);
            case PureExpression pureExpression -> valueArguments.add(pureExpression.evaluate(ctx));
            case StreamExpression ignored      -> throw new SaplCompilerException(ERROR_STREAM_IN_PURE_FILTER_ARGS);
            }
        }

        return valueArguments;
    }

    /**
     * Creates a flux that combines streaming arguments (Nature.STREAM).
     * <p>
     * Arguments contain StreamExpressions that emit values over time.
     *
     * @param arguments the compiled arguments
     * @param leftHandArg the left-hand argument (target value)
     * @return flux of argument value lists
     */
    static reactor.core.publisher.Flux<List<Value>> combineStreamArguments(CompiledArguments arguments,
            Value leftHandArg) {
        val sources = Arrays.stream(arguments.arguments()).map(ExpressionCompiler::compiledExpressionToFlux).toList();

        return reactor.core.publisher.Flux.combineLatest(sources, argValues -> {
            val valueArguments = new ArrayList<Value>(argValues.length + 1);
            valueArguments.add(leftHandArg);
            for (var argValue : argValues) {
                valueArguments.add((Value) argValue);
            }
            return (List<Value>) valueArguments;
        });
    }

}
