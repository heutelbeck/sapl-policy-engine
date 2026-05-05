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
package io.sapl.compiler.util;

import io.sapl.api.model.*;
import lombok.experimental.UtilityClass;

/**
 * Helper for evaluating a {@link CompiledExpression} known to be in the
 * pure/value stratum. Used by Reactor {@code stream()} paths in compilers
 * (function calls, array literals, object literals) where stream children
 * have already been handled by the caller and only Value/PureOperator
 * children remain to evaluate inline.
 * <p>
 * If a {@link StreamOperator} reaches this helper it indicates a programmer
 * bug in the calling compiler's categorisation. Per the no-throw rule for the
 * policy evaluation path, the helper returns an
 * {@link io.sapl.api.model.ErrorValue}
 * rather than throwing.
 */
@UtilityClass
public class PureOrValueEvaluator {

    private static final String ERROR_STREAM_REACHED_PURE_PATH = "Stream argument reached the pure-or-value evaluator path";

    /**
     * Evaluates the given expression assuming it is a {@link Value} or a
     * {@link PureOperator}. Returns an {@link io.sapl.api.model.ErrorValue}
     * if a {@link StreamOperator} is encountered.
     *
     * @param expr the expression to evaluate
     * @param ctx the evaluation context
     * @return the evaluated value, or an error value if a stream child
     * reaches this path
     */
    public static Value evaluate(CompiledExpression expr, EvaluationContext ctx) {
        return switch (expr) {
        case Value v                -> v;
        case PureOperator p         -> p.evaluate(ctx);
        case StreamOperator ignored -> Value.error(ERROR_STREAM_REACHED_PURE_PATH);
        };
    }
}
