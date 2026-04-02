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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.ast.ArrayExpression;
import io.sapl.ast.BinaryOperator;
import io.sapl.ast.BinaryOperatorType;
import io.sapl.ast.Expression;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Unrolls {@code EXPR in [a, b, c, ...]} into
 * {@code EXPR == a || EXPR == b || EXPR == c || ...} when the right-hand side
 * is an {@link ArrayExpression} with known length at compile time.
 * <p>
 * This enables the policy index to match individual equality comparisons and
 * the stratified boolean compiler to reorder and short-circuit optimally.
 * <ul>
 * <li>Empty array: constant {@code false}</li>
 * <li>Single element: {@code EXPR == element}</li>
 * <li>Two elements: binary {@code ||}</li>
 * <li>Three or more: n-ary {@code ||} via chained
 * {@link StratifiedBooleanOperationCompiler}</li>
 * </ul>
 * Array elements may be any expression (constant, pure, or stream). Each
 * {@code ==} compiles normally, and the n-ary {@code ||} automatically
 * stratifies: constants fold first, then pure expressions, then streams.
 */
@UtilityClass
class InArrayUnrollingCompiler {

    /**
     * Attempts to unroll an {@code IN} operation. Returns {@code null} if the
     * right-hand side is not an {@link ArrayExpression} (caller should fall
     * through to standard IN compilation).
     *
     * @param inOperator the IN operation
     * @param ctx compilation context
     * @return the unrolled expression, or {@code null} if not applicable
     */
    static CompiledExpression tryCompile(BinaryOperator inOperator, CompilationContext ctx) {
        if (!(inOperator.right() instanceof ArrayExpression arrayExpr)) {
            return null;
        }

        val haystack = arrayExpr.elements();
        val location = inOperator.location();

        // [] -> false
        if (haystack.isEmpty()) {
            return Value.FALSE;
        }

        // If needle is a compile-time constant undefined, fold to false
        // immediately. Undefined is never contained in any collection.
        val compiledNeedle = ExpressionCompiler.compile(inOperator.left(), ctx);
        if (compiledNeedle instanceof UndefinedValue) {
            return Value.FALSE;
        }

        // Build the equality chain: needle == a || needle == b || ...
        CompiledExpression orChain = compileEquality(inOperator, haystack.getFirst(), ctx);
        for (int i = 1; i < haystack.size(); i++) {
            val nextEq = compileEquality(inOperator, haystack.get(i), ctx);
            orChain = StratifiedBooleanOperationCompiler.compile(orChain, nextEq, BinaryOperatorType.LAZY_OR, location);
        }

        // If needle is a constant Value (and we already checked it's not
        // undefined above), no runtime guard needed. The or-chain is
        // enough.
        if (compiledNeedle instanceof Value) {
            return orChain;
        }

        // For runtime needle (PureOperator/StreamOperator), guard against
        // undefined: compiledNeedle != undefined && (or-chain).
        // The stratified && short-circuits: if needle is undefined at runtime,
        // the or-chain is never evaluated, matching isContainedIn semantics.
        val                neOp = BinaryOperationCompiler.BINARY_OPERATIONS.get(BinaryOperatorType.NE);
        CompiledExpression undefinedGuard;
        if (compiledNeedle instanceof StreamOperator s) {
            undefinedGuard = new BinaryOperationCompiler.BinaryStreamValue(neOp, s, Value.UNDEFINED, location);
        } else {
            val p = (PureOperator) compiledNeedle;
            undefinedGuard = new BinaryOperationCompiler.BinaryPureValue(BinaryOperatorType.NE, neOp, p,
                    Value.UNDEFINED, location, p.isDependingOnSubscription(), p.isRelativeExpression());
        }
        return StratifiedBooleanOperationCompiler.compile(undefinedGuard, orChain, BinaryOperatorType.LAZY_AND,
                location);
    }

    private static CompiledExpression compileEquality(BinaryOperator inOperator, Expression arrayElement,
            CompilationContext ctx) {
        return BinaryOperationCompiler.compile(
                new BinaryOperator(BinaryOperatorType.EQ, inOperator.left(), arrayElement, inOperator.location()), ctx);
    }

}
