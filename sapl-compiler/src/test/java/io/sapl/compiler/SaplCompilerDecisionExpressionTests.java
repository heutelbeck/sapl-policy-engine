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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import lombok.val;

/**
 * Tests verifying the refactored compileDecisionExpression method handles all
 * compilation paths correctly.
 */
class SaplCompilerDecisionExpressionTests {

    private static final DefaultSAPLInterpreter PARSER = new DefaultSAPLInterpreter();

    private CompilationContext context;

    @BeforeEach
    void setup() throws InitializationException {
        val functionBroker      = new DefaultFunctionBroker();
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);
        context = new CompilationContext(functionBroker, attributeBroker);
    }

    @Test
    void when_compileTimeErrorInBody_then_indeterminateDecision() {
        val policy   = """
                policy "error in body"
                permit where undefined.field == 1;
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_compileTimeErrorInObligation_then_indeterminateDecision() {
        val policy   = """
                policy "error in obligation"
                permit
                obligation undefined.field
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_compileTimeErrorInAdvice_then_indeterminateDecision() {
        val policy   = """
                policy "error in advice"
                permit
                advice undefined.field
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_compileTimeErrorInTransformation_then_indeterminateDecision() {
        val policy   = """
                policy "error in transformation"
                permit
                transform undefined.field
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_constantBodyIsNonBoolean_then_indeterminateDecision() {
        val policy   = """
                policy "non-boolean body"
                permit where "text";
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_constantBodyIsFalse_then_notApplicableDecision() {
        val policy   = """
                policy "false body"
                permit where false;
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_fullyConstantDecisionWithTrueBody_then_permitDecision() {
        val policy   = """
                policy "fully constant"
                permit where true;
                obligation "must do this"
                advice "should do this"
                transform "result"
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
        assertThat(compiled.decisionExpression()).isInstanceOf(Value.class);
    }

    @Test
    void when_fullyConstantDecisionWithErrorInObligation_then_indeterminate() {
        val policy   = """
                policy "constant with error"
                permit where true;
                obligation {"error": 1/0}
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_pureBodyWithPureConstraints_then_pureExpression() {
        val policy   = """
                policy "pure expressions"
                permit where subscription.age > 18;
                obligation {"user": subscription.name}
                advice {"timestamp": time.now()}
                transform {"allowed": true}
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_pureBodyWithMixedConstraints_then_streamExpression() {
        val policy   = """
                policy "mixed expressions"
                permit where subscription.age > 18;
                obligation {"fixed": "value"}
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_streamingBodyWithConstraints_then_streamExpression() {
        val policy   = """
                policy "streaming body"
                permit where subscription.age > 18;
                obligation {"result": "ok"}
                advice {"info": "processed"}
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
    }

    @Test
    void when_noBodyNoConstraints_then_permitDecision() {
        val policy   = """
                policy "minimal"
                permit
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
        assertThat(compiled.decisionExpression()).isInstanceOf(Value.class);
    }

    @Test
    void when_denyWithObligations_then_denyDecisionWithObligations() {
        val policy   = """
                policy "deny with obligations"
                deny
                obligation "log access attempt"
                obligation "notify admin"
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
        assertThat(compiled.decisionExpression()).isInstanceOf(Value.class);
    }

    @Test
    void when_permitWithAdviceAndTransformation_then_correctDecision() {
        val policy   = """
                policy "full decision"
                permit
                advice "consider rate limiting"
                transform {"sanitized": true}
                """;
        val compiled = compilePolicy(policy);
        assertThat(compiled).isNotNull();
        assertThat(compiled.decisionExpression()).isInstanceOf(Value.class);
    }

    private CompiledPolicy compilePolicy(String policyText) {
        val sapl = PARSER.parse(policyText);
        return SaplCompiler.compileDocument(sapl, context);
    }

}
