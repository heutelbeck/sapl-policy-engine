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

import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests verifying the refactored compileDecisionExpression method handles all
 * compilation paths correctly.
 */
class SaplCompilerDecisionExpressionTests {

    private static final DefaultSAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    private CompilationContext context;

    @BeforeEach
    void setup() {
        val functionBroker      = new DefaultFunctionBroker();
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);
        context = new CompilationContext(functionBroker, attributeBroker);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesCompilingSuccessfully")
    void whenPolicy_thenCompilesSuccessfully(String description, String policy) {
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    static Stream<Arguments> policiesCompilingSuccessfully() {
        return Stream.of(arguments("compile-time error in body yields indeterminate", """
                policy "error in body"
                permit where undefined.field == 1;
                """), arguments("compile-time error in obligation yields indeterminate", """
                policy "error in obligation"
                permit
                obligation undefined.field
                """), arguments("compile-time error in advice yields indeterminate", """
                policy "error in advice"
                permit
                advice undefined.field
                """), arguments("compile-time error in transformation yields indeterminate", """
                policy "error in transformation"
                permit
                transform undefined.field
                """), arguments("constant non-boolean body yields indeterminate", """
                policy "non-boolean body"
                permit where "text";
                """), arguments("constant false body yields not applicable", """
                policy "false body"
                permit where false;
                """), arguments("constant body with error in obligation yields indeterminate", """
                policy "constant with error"
                permit where true;
                obligation {"error": 1/0}
                """), arguments("pure body with pure constraints yields pure expression", """
                policy "pure expressions"
                permit where subscription.age > 18;
                obligation {"user": subscription.name}
                advice {"timestamp": time.now()}
                transform {"allowed": true}
                """), arguments("pure body with mixed constraints yields stream expression", """
                policy "mixed expressions"
                permit where subscription.age > 18;
                obligation {"fixed": "value"}
                """), arguments("streaming body with constraints yields stream expression", """
                policy "streaming body"
                permit where subscription.age > 18;
                obligation {"result": "ok"}
                advice {"info": "processed"}
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesCompilingToConstantDecision")
    void whenFullyConstantPolicy_thenDecisionExpressionIsValue(String description, String policy) {
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
        assertThat(compiled.decisionExpression()).isInstanceOf(Value.class);
    }

    static Stream<Arguments> policiesCompilingToConstantDecision() {
        return Stream.of(arguments("fully constant with true body yields permit", """
                policy "fully constant"
                permit where true;
                obligation "must do this"
                advice "should do this"
                transform "result"
                """), arguments("minimal permit policy", """
                policy "minimal"
                permit
                """), arguments("deny with obligations yields deny with obligations", """
                policy "deny with obligations"
                deny
                obligation "log access attempt"
                obligation "notify admin"
                """), arguments("permit with advice and transformation", """
                policy "full decision"
                permit
                advice "consider rate limiting"
                transform {"sanitized": true}
                """));
    }

    private CompiledPolicy compilePolicy(String policyText) {
        val sapl = PARSER.parse(policyText);
        return SaplCompiler.compileDocument(sapl, context);
    }

}
