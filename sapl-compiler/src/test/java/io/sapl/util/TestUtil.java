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
package io.sapl.util;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.SaplCompiler;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class TestUtil {
    private static final FunctionBroker FUNCTION_BROKER = new DefaultFunctionBroker();
    private static final SaplCompiler   SAPL_COMPILER   = new SaplCompiler(FUNCTION_BROKER);

    private CompilationContext createCompilationContext() {
        return new CompilationContext();
    }

    private EvaluationContext createEvaluationContext(AuthorizationSubscription authorizationSubscription) {
        return new EvaluationContext(authorizationSubscription, FUNCTION_BROKER);
    }

    private EvaluationContext createEvaluationContext() {
        return createEvaluationContext(new AuthorizationSubscription(Value.of("Elric"), Value.of("slay"),
                Value.of("Moonglum"), Value.of("Tanelorn")));
    }

    public static void assertCompiledExpressionEvaluatesTo(String expression, Value expected) {
        val compiledExpression = compileExpression(expression);
        val evaluated          = evaluateExpression(compiledExpression, createEvaluationContext());
        StepVerifier.create(evaluated).expectNext(expected).verifyComplete();
    }

    public static void assertExpressionCompilesToValue(String expression, Value expected) {
        val compiledExpression = compileExpression(expression);
        assertThat(compiledExpression).isEqualTo(expected);
    }

    @SneakyThrows
    private CompiledExpression compileExpression(String expression) {
        val parsedExpression = ParserUtil.expression(expression);
        return SAPL_COMPILER.compileExpression(parsedExpression, createCompilationContext());
    }

    private Flux<Value> evaluateExpression(CompiledExpression expression, EvaluationContext evaluationContext) {
        return switch (expression) {
        case Value value                       -> Flux.just(value);
        case PureExpression pureExpression     -> Flux.just(pureExpression.evaluate(evaluationContext));
        case StreamExpression streamExpression ->
            streamExpression.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext));
        };
    }

}
