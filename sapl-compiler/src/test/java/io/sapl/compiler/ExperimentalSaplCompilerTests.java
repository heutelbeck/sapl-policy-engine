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
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.ArrayFunctionLibrary;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.util.TestUtil;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;

import static io.sapl.util.TestUtil.assertExpressionCompilesToValue;

class ExperimentalSaplCompilerTests {
    private static final SAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    private CompilationContext createCompilationContext() throws InitializationException {
        val functionBroker      = new DefaultFunctionBroker();
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);

        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(ArrayFunctionLibrary.class);

        return new CompilationContext(functionBroker, attributeBroker);
    }

    @Test
    void when_experimentWithCompiler_then_compilesPolicy() throws InitializationException {
        val source  = """
                policy "test policy"
                permit 7[?(@>subscription.age)]
                // where
                  // resource.id == "def";
                """;
        val sapl    = PARSER.parse(source);
        val context = createCompilationContext();
        try {
            val compiled = SaplCompiler.compileDocument(sapl, context);
            System.err.println(compiled);
        } catch (SaplCompilerException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    void when_experimentWithPolicySet_then_compilesPolicySet() throws InitializationException {
        val source  = """
                set "test"
                first-applicable

                policy "test policy"
                permit
                advice {"hello":"world"}
                """;
        val sapl    = PARSER.parse(source);
        val context = createCompilationContext();
        try {
            val compiled = SaplCompiler.compileDocument(sapl, context);
            System.err.println(compiled);
        } catch (SaplCompilerException e) {
            System.err.println(e.getMessage());
        }
    }

    @Test
    void when_constantFolding_then_optimizesCorrectly() {
        val expression = """
                { "key1": 123 }
                """;
        val expected   = ObjectValue.builder().put("key1", Value.of(123)).build();
        assertExpressionCompilesToValue(expression, expected);
    }

    @Test
    @Timeout(5)
    void when_constantAttributes_then_evaluatesCorrectly() {
        val expression = """
                "123".<test.echo> == "123"
                """;
        TestUtil.evaluateExpression(expression).doOnNext(System.err::println).take(1).blockLast(Duration.ofSeconds(5));
    }
}
