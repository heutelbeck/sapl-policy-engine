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
import io.sapl.api.model.Value;
import io.sapl.compiler.index.SemanticHashing;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The canonical order of the operands of the commutative operators
 * {@code ==}, {@code !=}, {@code *}, {@code &&}, {@code ||}, {@code &},
 * {@code |} and {@code ^}. Ordered canonically, {@code a == b} and
 * {@code b == a} compile to the same operator, so they are semantically equal,
 * hash alike and form one predicate in the policy index.
 * <p>
 * The strata keep their order of evaluation: constants first, then pure
 * operands, then streams. Within a stratum, constants are ordered by the hash
 * of their value and pure operands by their semantic hash. Streams keep the
 * order written, as they have no semantic hash. Operands with equal hashes
 * keep the order written as well.
 * <p>
 * As operands are evaluated in this order, of several errors among the
 * operands the first in canonical order is the result.
 */
@UtilityClass
class CanonicalOperandOrder {

    private static final int CONSTANTS = 0;
    private static final int PURES     = 1;
    private static final int STREAMS   = 2;

    private static final Comparator<CompiledExpression> ORDER = Comparator.comparingInt(CanonicalOperandOrder::stratum)
            .thenComparingLong(CanonicalOperandOrder::hashInStratum);

    /**
     * @param operand an operand
     * @param other another operand
     * @return true if the operand comes before the other in canonical order
     */
    static boolean precedes(CompiledExpression operand, CompiledExpression other) {
        return ORDER.compare(operand, other) < 0;
    }

    /**
     * @param operands the operands in the order written
     * @param <T> the type of the operands
     * @return a new list of the operands in canonical order
     */
    static <T extends CompiledExpression> List<T> sorted(List<T> operands) {
        val sorted = new ArrayList<>(operands);
        sorted.sort(ORDER);
        return sorted;
    }

    private static int stratum(CompiledExpression operand) {
        return switch (operand) {
        case Value ignored          -> CONSTANTS;
        case PureOperator ignored   -> PURES;
        case StreamOperator ignored -> STREAMS;
        };
    }

    private static long hashInStratum(CompiledExpression operand) {
        return switch (operand) {
        case Value value            -> SemanticHashing.valueHash(value);
        case PureOperator pure      -> pure.semanticHash();
        case StreamOperator ignored -> 0L;
        };
    }
}
