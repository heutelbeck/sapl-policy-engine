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

import io.sapl.api.value.NumberValue;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.plugins.DefaultStaticPluginsServer;
import io.sapl.plugins.MethodSignatureProcessor;
import io.sapl.plugins.TemporalFunctionLibrary;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
public class SaplCompilerTests {
    private static final SAPLInterpreter            PARSER   = new DefaultSAPLInterpreter();
    private static final DefaultStaticPluginsServer PLUGINS  = new DefaultStaticPluginsServer();
    private static final SaplCompiler               COMPILER = new SaplCompiler(PLUGINS);

    @Test
    void experimentWithCompiler() throws NoSuchMethodException, InitializationException {
        val method = TemporalFunctionLibrary.class.getMethod("durationOfSeconds", NumberValue.class);

        val spec = MethodSignatureProcessor.functionSpecification(null, "time", method);
        Assertions.assertNotNull(spec);
        PLUGINS.loadFunction(spec);
        val source  = """
                policy "test policy"
                permit time.durationOfSeconds(1234)
                // where
                  // resource.id == "def";
                """;
        val sapl    = PARSER.parse(source);
        val context = new CompilationContext();
        try {
            val compiled = COMPILER.compileDocument(sapl, context);
            System.err.println(compiled);
        } catch (SaplCompilerException e) {
            System.err.println(e.getMessage());
        }
    }
}
