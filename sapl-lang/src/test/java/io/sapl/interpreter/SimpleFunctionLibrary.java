/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;

@FunctionLibrary(name = "simple", description = "some simple functions")
public class SimpleFunctionLibrary {

    @Function
    public Val length(Val parameter) {
        Val result = Val.UNDEFINED;
        if (parameter.isArray()) {
            result = Val.of(parameter.get().size());
        } else if (parameter.isTextual()) {
            result = Val.of(parameter.get().asText().length());
        } else {
            throw new PolicyEvaluationException(
                    "length() parameter must be a string or an array, found " + parameter + ".");
        }
        return result;
    }

    @Function
    public Val append(@Text @Number Val... parameters) {
        StringBuilder builder = new StringBuilder();
        for (Val parameter : parameters) {
            if (parameter.isTextual()) {
                builder.append(parameter.get().asText());
            } else if (parameter.isNumber()) {
                builder.append(parameter.get().asInt());
            }
        }
        return Val.of(builder.toString());
    }

}
