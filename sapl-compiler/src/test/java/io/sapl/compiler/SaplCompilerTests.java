/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.ArrayFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import static io.sapl.util.TestUtil.assertExpressionCompilesToValue;

@Slf4j
class SaplCompilerTests {
    private static final SAPLInterpreter       PARSER          = new DefaultSAPLInterpreter();
    private static final DefaultFunctionBroker FUNCTION_BROKER = new DefaultFunctionBroker();

    @Test
    void experimentWithCompiler() throws InitializationException {
        FUNCTION_BROKER.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        FUNCTION_BROKER.loadStaticFunctionLibrary(ArrayFunctionLibrary.class);
        val source  = """
                policy "test policy"
                permit 7[?(@>subscription.age)]
                // where
                  // resource.id == "def";
                """;
        val sapl    = PARSER.parse(source);
        val context = new CompilationContext(FUNCTION_BROKER);
        try {
            val compiled = SaplCompiler.compileDocument(sapl, context);
            System.err.println(compiled);
        } catch (SaplCompilerException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    void constantFoldingWorks() {
        val expression = """
                { "key1": 123 }
                """;
        val expected   = ObjectValue.builder().put("key1", Value.of(123)).build();
        assertExpressionCompilesToValue(expression, expected);
    }
}
