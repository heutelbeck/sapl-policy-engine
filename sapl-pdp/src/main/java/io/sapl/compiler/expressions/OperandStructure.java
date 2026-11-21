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

import io.sapl.api.model.PureOperator;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The structure of a pure operand by which the operands of commutative
 * operators take their canonical order: the name of its kind and its parts, as
 * the specification of the canonical order in the conformance corpus states
 * them. A part is an operand ({@link io.sapl.api.model.CompiledExpression}), a
 * text ({@link String}), an integer ({@link Integer}), the element of a filter
 * path (an {@link OperandStructure} without a base), or absent ({@code null}).
 *
 * @param kind the name of the kind
 * @param parts the parts in the order they are compared
 */
public record OperandStructure(String kind, List<@Nullable Object> parts) {

    /**
     * @param kind the name of the kind
     * @param parts the parts in the order they are compared
     * @return the structure
     */
    public static OperandStructure of(String kind, @Nullable Object... parts) {
        return new OperandStructure(kind, Arrays.asList(parts));
    }

    /**
     * A pure operator that states its structure itself, as operators do that are
     * compiled outside this package.
     */
    public interface StructuredOperator extends PureOperator {

        /**
         * @return the structure of the operator
         */
        OperandStructure structure();
    }
}
