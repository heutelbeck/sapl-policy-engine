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
package io.sapl.ast;

import io.sapl.api.model.SourceLocation;
import io.sapl.compiler.SaplCompilerException;
import lombok.NonNull;

import java.util.List;

/**
 * N-ary product (multiplication) for 3+ operands.
 * Enables constant folding across non-constant expressions.
 *
 * @param operands list of operands (at least 3)
 * @param location source location spanning all operands
 */
public record Product(@NonNull List<Expression> operands, @NonNull SourceLocation location) implements Expression {

    public Product {
        if (operands.size() < 3) {
            throw new SaplCompilerException("Product requires at least 3 operands, use BinaryOperator for 2", location);
        }
        operands = List.copyOf(operands);
    }

}
