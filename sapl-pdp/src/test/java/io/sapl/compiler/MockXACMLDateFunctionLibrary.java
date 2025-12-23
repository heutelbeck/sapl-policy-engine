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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;

/**
 * Mock date function library for XACML-style tests. Adapted from sapl-lang to
 * use Value instead of Val. Returns
 * hardcoded values for testing purposes.
 */
@FunctionLibrary(name = "date")
public class MockXACMLDateFunctionLibrary {

    @Function
    public static Value diff(Value type, Value to, Value from) {
        if (!(type instanceof TextValue typeVal)) {
            return Value.error("date.diff requires text value as type argument");
        }
        if ("years".equals(typeVal.value())) {
            return Value.of(15L);
        }
        return Value.of(5L);
    }

}
