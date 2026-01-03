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
package io.sapl.compiler;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.ast.AstNode;
import lombok.experimental.UtilityClass;

/**
 * Boolean operations for SAPL expression evaluation.
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
    public static Value not(AstNode op, Value v) {
        if (v instanceof ErrorValue)
            return v;
        if (v instanceof BooleanValue(boolean val))
            return val ? Value.FALSE : Value.TRUE;
        return Value.errorAt(op, ERROR_TYPE_MISMATCH, v);
    }

}
