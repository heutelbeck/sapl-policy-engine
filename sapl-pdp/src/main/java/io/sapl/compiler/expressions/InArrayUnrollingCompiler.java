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
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Bounds the equality predicates the binary compiler exposes to the index for
 * a short input path tested against constant scalar array elements.
 */
@UtilityClass
class InArrayUnrollingCompiler {

    private static final int MAX_ELEMENTS   = 64;
    private static final int MAX_PATH_STEPS = 16;

    static boolean reserveExpansion(PureOperator needle, ArrayValue haystack, CompilationContext ctx) {
        if (haystack.isEmpty() || haystack.size() > MAX_ELEMENTS) {
            return false;
        }
        val pathCost = inputPathCost(needle);
        if (pathCost == 0) {
            return false;
        }
        // Every equality and the defined guard evaluate the input. Each also adds at most
        // two predicate nodes, including the disjunctions and the final conjunction.
        long cost = (haystack.size() + 1) * (pathCost + 2);
        for (val element : haystack) {
            val elementCost = scalarCost(element);
            if (elementCost == 0) {
                return false;
            }
            cost += elementCost;
        }
        return ctx.reserveMembershipExpansion(cost);
    }

    // A variable may stand for an arbitrarily large compiled operand. Count its actual
    // input path and leave any other computation, including an earlier rewrite, intact.
    private static long inputPathCost(PureOperator needle) {
        PureOperator current = needle;
        long         cost    = 1;
        for (int depth = 0; depth <= MAX_PATH_STEPS; depth++) {
            if (current instanceof ExpressionCompiler.IdentifierOp
                    || current instanceof ExpressionCompiler.RelativeValueOp
                    || current instanceof ExpressionCompiler.RelativeLocationOp) {
                return cost;
            }
            if (current instanceof StepCompiler.KeyStepPure step) {
                cost    += 1L + step.key().length();
                current  = step.base();
            } else if (current instanceof StepCompiler.IndexStepPure step) {
                cost++;
                current = step.base();
            } else {
                return 0;
            }
        }
        return 0;
    }

    // Ordering the generated comparisons can inspect the constants. Containers may
    // share arbitrarily large subtrees and retain their ordinary membership test.
    private static long scalarCost(Value value) {
        return switch (value) {
        case NullValue ignored    -> 1L;
        case BooleanValue ignored -> 1L;
        case TextValue text       -> 1L + text.value().length();
        case NumberValue number   -> {
            val decimal = number.value();
            yield 1L + decimal.unscaledValue().abs().bitLength() / Byte.SIZE + Math.abs((long) decimal.scale());
        }
        default                   -> 0L;
        };
    }

}
