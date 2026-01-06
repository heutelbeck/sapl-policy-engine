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
import lombok.experimental.UtilityClass;

/**
 * Boolean operations for SAPL expression evaluation.
 * <p>
 * Note: AND and OR use cost-stratified short-circuit evaluation via
 * {@link io.sapl.compiler.LazyBooleanOperationCompiler} and are not in this
 * class.
 * <p>
 * All operations return {@link ErrorValue} for type mismatches rather than
 * throwing exceptions. Error values propagate through operations.
 */
@UtilityClass
public class BooleanOperators {

    private static final String ERROR_TYPE_MISMATCH = "Logical op requires boolean value, but found: %s.";

    /**
     * Logical negation.
     */
    public static Value not(Value v, SourceLocation location) {
        if (v instanceof BooleanValue(boolean val))
            return val ? Value.FALSE : Value.TRUE;
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, v);
    }

    /**
     * Logical XOR (exclusive or).
     * <p>
     * XOR is the only non-short-circuit boolean operator since both operands
     * must always be evaluated to determine the result.
     */
    public static Value xor(Value a, Value b, SourceLocation location) {
        if (a instanceof BooleanValue(var va) && b instanceof BooleanValue(var vb)) {
            return va ^ vb ? Value.TRUE : Value.FALSE;
        }
        return Value.errorAt(location, ERROR_TYPE_MISMATCH, !(a instanceof BooleanValue) ? a : b);
    }

}
