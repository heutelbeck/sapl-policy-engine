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
package io.sapl.compiler.operators;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler;
import lombok.experimental.UtilityClass;

/**
 * Boolean operations for SAPL expression evaluation.
 * <p>
 * Note: AND and OR use cost-stratified short-circuit evaluation via
 * {@link StratifiedBooleanOperationCompiler} and are not in this
 * class.
 * <p>
 * All operations return {@link ErrorValue} for type mismatches rather than
 * throwing exceptions. Error values propagate through operations.
 */
@UtilityClass
public class BooleanOperators {

    private static final String ERROR_TYPE_MISMATCH = "Logical operation requires boolean value, but found: %s.";

    /**
     * Logical negation.
     */
    public static Value not(Value v, SourceLocation location) {
        if (v instanceof BooleanValue(boolean val))
            return val ? Value.FALSE : Value.TRUE;
        return typeMismatch(v, location);
    }

    /**
     * Logical XOR (exclusive or).
     * <p>
     * Both operands must be boolean to compute a boolean result.
     */
    public static Value xor(Value a, Value b, SourceLocation location) {
        if (a instanceof BooleanValue(var va) && b instanceof BooleanValue(var vb)) {
            return va ^ vb ? Value.TRUE : Value.FALSE;
        }
        return typeMismatch(a instanceof BooleanValue ? b : a, location);
    }

    /**
     * Reports a non-boolean operand of a logical operation.
     *
     * @param operand the offending value
     * @param location source of the logical operation
     * @return the type error at that location
     */
    public static ErrorValue typeMismatch(Value operand, SourceLocation location) {
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, operand);
    }

}
