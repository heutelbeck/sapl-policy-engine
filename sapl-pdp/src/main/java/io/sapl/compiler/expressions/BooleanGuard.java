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

import io.sapl.api.model.*;
import io.sapl.ast.Expression;
import io.sapl.ast.UnaryOperator;
import io.sapl.compiler.expressions.NaryBooleanCompiler.NaryBooleanPure;
import io.sapl.compiler.expressions.NaryBooleanCompiler.NaryBooleanStreamEager;
import io.sapl.compiler.expressions.NaryBooleanCompiler.NaryBooleanStreamLazy;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.LazyValuePure;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.LazyValueStream;
import io.sapl.compiler.operators.BooleanOperators;
import lombok.experimental.UtilityClass;
import lombok.val;

import static io.sapl.ast.UnaryOperatorType.CHECK_BOOLEAN;

/** Boolean type checks shared by the logical operators. */
@UtilityClass
class BooleanGuard {
    static final UnaryOperation BOOLEAN_GUARD = booleanGuard(BooleanOperators::typeMismatch);

    static UnaryOperation booleanGuard(UnaryOperation onTypeMismatch) {
        return (value, location) -> value instanceof BooleanValue || value instanceof ErrorValue ? value
                : onTypeMismatch.apply(value, location);
    }

    static CompiledExpression compileBooleanOperand(Expression operand, CompilationContext ctx) {
        return withoutBooleanGuard(ExpressionCompiler.compile(withoutBooleanGuard(operand), ctx));
    }

    static SourceLocation operandLocation(Expression operand) {
        return withoutBooleanGuard(operand).location();
    }

    private static Expression withoutBooleanGuard(Expression operand) {
        return operand instanceof UnaryOperator(var type, var inner, var ignored) && type == CHECK_BOOLEAN ? inner
                : operand;
    }

    static CompiledExpression withoutBooleanGuard(CompiledExpression operand) {
        if (operand instanceof CompiledUnary unary && unary.op() == BOOLEAN_GUARD) {
            return unary.operand();
        }
        if (operand instanceof LazyValuePure pure) {
            return pure.p();
        }
        if (operand instanceof LazyValueStream stream) {
            return stream.s();
        }
        // A folded junction also carries any constant error and every remaining dependency.
        if (operand instanceof NaryBooleanPure pure && pure.pendingError() == null && pure.operands().size() == 1) {
            return pure.operands().getFirst();
        }
        if (operand instanceof NaryBooleanStreamLazy lazy && lazy.pendingError() == null && lazy.pures().isEmpty()
                && lazy.streams().size() == 1) {
            return lazy.streams().getFirst();
        }
        if (operand instanceof NaryBooleanStreamEager eager && eager.pendingError() == null && eager.pures().isEmpty()
                && eager.streams().size() == 1) {
            return eager.streams().getFirst();
        }
        return operand;
    }

    interface CompiledUnary {
        UnaryOperation op();

        CompiledExpression operand();

        SourceLocation location();
    }

    /** An operation that returns a boolean on success. */
    interface BooleanResult {
        default boolean hasBooleanResult() {
            return true;
        }
    }

    /** Binary and n-ary nodes carry the function selected for their operator. */
    interface BinaryResult extends BooleanResult {
        BinaryOperation op();

        @Override
        default boolean hasBooleanResult() {
            return op() instanceof Predicate;
        }
    }

    @FunctionalInterface
    interface Predicate extends BinaryOperation {
    }
}
