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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

/**
 * Function library implementing the blacken, replace, and remove filter
 * functions.
 *
 * @author Dominic Heutelbeck
 */
@UtilityClass
@FunctionLibrary(name = "SchemaTestLibrary")
public class SchemaTestFunctionLibrary {

    static final String PERSON_SCHEMA = "{\"name\": {\"type\": \"string\"}, \"age\": {\"type\": \"number\"}}";

    @Function(name = "schemaFun", schema = PERSON_SCHEMA)
    public static Val schemaFun() {
        return Val.of(true);
    }

}
