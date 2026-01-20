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
package io.sapl.compiler.policyset;

import io.sapl.api.model.*;
import io.sapl.ast.Expression;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.compiler.expressions.LazyBooleanOperationCompiler;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policy.policybody.BooleanGuardCompiler;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class TargetExpressionCompiler {
    private static final String ERROR_TARGET_NOT_BOOLEAN       = "Target expressions must evaluate to Boolean, but got %s.";
    private static final String ERROR_TARGET_RELATIVE_ACCESSOR = "The target expression contains a top-level relative value accessor (@ or #) outside of any expression that may set its value.";
    private static final String ERROR_TARGET_STATIC_ERROR      = "The target expression statically evaluates to an errors: %s.";
    private static final String ERROR_TARGET_STREAM_OPERATOR   = "Target expression must not contain attributes operators <>!.";

    public CompiledExpression compileTargetExpression(Expression targetExpression, CompiledExpression schemaValidator,
            CompilationContext ctx) {
        val compiledTarget = targetExpression == null ? Value.TRUE
                : BooleanGuardCompiler.applyBooleanGuard(ExpressionCompiler.compile(targetExpression, ctx),
                        targetExpression.location(), ERROR_TARGET_NOT_BOOLEAN);
        if (compiledTarget instanceof ErrorValue error) {
            throw new SaplCompilerException(ERROR_TARGET_STATIC_ERROR.formatted(error), targetExpression.location());
        }
        if (compiledTarget instanceof PureOperator po && !po.isDependingOnSubscription()) {
            throw new SaplCompilerException(ERROR_TARGET_RELATIVE_ACCESSOR, targetExpression.location());
        }
        if (compiledTarget instanceof StreamOperator) {
            throw new SaplCompilerException(ERROR_TARGET_STREAM_OPERATOR, targetExpression.location());
        }
        if (schemaValidator != null) {
            if (targetExpression == null) {
                return schemaValidator;
            }
            return LazyBooleanOperationCompiler.compile(schemaValidator, compiledTarget, true,
                    targetExpression.location());
        }

        return compiledTarget;
    }
}
