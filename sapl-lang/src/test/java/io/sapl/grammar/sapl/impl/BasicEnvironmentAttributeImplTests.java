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
package io.sapl.grammar.sapl.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.testutil.MockUtil;
import io.sapl.testutil.ParserUtil;
import lombok.val;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;

import static io.sapl.testutil.TestUtil.assertExpressionErrors;
import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import reactor.util.context.Context;
import static org.assertj.core.api.Assertions.assertThat;

class BasicEnvironmentAttributeImplTests {

    private static final SaplFactory FACTORY                   = SaplFactoryImpl.eINSTANCE;
    private static final String      ATTRIBUTE                 = "attribute";
    private static final String      FULLY_QUALIFIED_ATTRIBUTE = "mock." + ATTRIBUTE;
    private static final String[]    ZERO_TO_FIVE              = new String[] { "0", "1", "2", "3", "4", "5" };

    @Test
    void evaluateBasicAttributeFlux() {
        final var expression = "<test.numbers>";
        assertExpressionEvaluatesTo(expression, ZERO_TO_FIVE);
    }

    @Test
    void evaluateBasicAttributeInTargetPolicy() throws IOException {
        final var expression = ParserUtil.expression("<test.numbers>");
        MockUtil.mockPolicyTargetExpressionContainerExpression(expression);
        assertExpressionErrors(expression);
    }

    @Test
    void evaluateBasicAttributeInTargetPolicySet() throws IOException {
        final var expression = ParserUtil.expression("<test.numbers>");
        MockUtil.mockPolicySetTargetExpressionContainerExpression(expression);
        assertExpressionErrors(expression);
    }

    @Test
    void exceptionDuringEvaluation() {
        final var step = attributeFinderStep();
        final var sut  = step.evaluate().contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx,
                mockAttributeStreamBrokerWithStream(Flux.just(ErrorFactory.error("ERROR")))));
        StepVerifier.create(sut).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void applyWithSomeStreamData() {
        Val[]     data = { Val.FALSE, ErrorFactory.error("ERROR"), Val.TRUE, Val.NULL, Val.UNDEFINED };
        final var step = attributeFinderStep();
        final var sut  = step.evaluate().contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx,
                mockAttributeStreamBrokerWithStream(Flux.just(data))));
        StepVerifier.create(sut).expectNext(data).verifyComplete();
    }

    private static AttributeStreamBroker mockAttributeStreamBrokerWithStream(Flux<Val> stream) {
        final var attributeBroker = mock(AttributeStreamBroker.class);
        when(attributeBroker.attributeStream(any())).thenReturn(stream);
        return attributeBroker;
    }

    private static BasicEnvironmentAttribute attributeFinderStep() {
        final var step = FACTORY.createBasicEnvironmentAttribute();
        step.eSet(step.eClass().getEStructuralFeature("identifier"), FACTORY.createFunctionIdentifier());
        step.getIdentifier().getNameFragments().add(FULLY_QUALIFIED_ATTRIBUTE);
        return step;
    }

    @Test
    void attributeFinderInvocationRespectsDefaultOptions() {
        val capturedInvocation = new AtomicReference<AttributeFinderInvocation>();
        val capturingBroker    = mockCapturingAttributeStreamBroker(capturedInvocation);

        val expression        = attributeFinderStep();
        val evaluationContext = AuthorizationContext.setAttributeStreamBroker(
                AuthorizationContext.setVariables(Context.empty(), Map.of()), capturingBroker);

        StepVerifier.create(expression.evaluate().contextWrite(evaluationContext)).expectNextCount(1).verifyComplete();

        val invocation = capturedInvocation.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.initialTimeOut()).isEqualTo(Duration.ofMillis(3000L));
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(30000L));
        assertThat(invocation.backoff()).isEqualTo(Duration.ofMillis(1000L));
        assertThat(invocation.retries()).isEqualTo(3);
        assertThat(invocation.fresh()).isFalse();
    }

    @Test
    void attributeFinderInvocationRespectsLocalOptions() throws IOException {
        val capturedInvocation = new AtomicReference<AttributeFinderInvocation>();
        val capturingBroker    = mockCapturingAttributeStreamBroker(capturedInvocation);

        val expression = ParserUtil.expression("""
                <test.attribute[{
                    "initialTimeOutMs": 5000,
                    "pollIntervalMs": 60000,
                    "backoffMs": 2000,
                    "retries": 5,
                    "fresh": true
                }]>
                """);

        val evaluationContext = AuthorizationContext.setAttributeStreamBroker(
                AuthorizationContext.setVariables(Context.empty(), Map.of()), capturingBroker);

        StepVerifier.create(expression.evaluate().contextWrite(evaluationContext)).expectNextCount(1).verifyComplete();

        val invocation = capturedInvocation.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.initialTimeOut()).isEqualTo(Duration.ofMillis(5000L));
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(60000L));
        assertThat(invocation.backoff()).isEqualTo(Duration.ofMillis(2000L));
        assertThat(invocation.retries()).isEqualTo(5);
        assertThat(invocation.fresh()).isTrue();
    }

    @Test
    void attributeFinderInvocationRespectsGlobalOptions() throws IOException {
        val capturedInvocation = new AtomicReference<AttributeFinderInvocation>();
        val capturingBroker    = mockCapturingAttributeStreamBroker(capturedInvocation);

        val expression = ParserUtil.expression("<test.attribute>");

        val globalOptions = Val.ofJson("""
                {
                    "attributeFinderOptions": {
                        "initialTimeOutMs": 7000,
                        "pollIntervalMs": 45000,
                        "backoffMs": 1500,
                        "retries": 7,
                        "fresh": true
                    }
                }
                """);

        val evaluationContext = AuthorizationContext.setAttributeStreamBroker(
                AuthorizationContext.setVariables(Context.empty(), Map.of("SAPL", globalOptions)), capturingBroker);

        StepVerifier.create(expression.evaluate().contextWrite(evaluationContext)).expectNextCount(1).verifyComplete();

        val invocation = capturedInvocation.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.initialTimeOut()).isEqualTo(Duration.ofMillis(7000L));
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(45000L));
        assertThat(invocation.backoff()).isEqualTo(Duration.ofMillis(1500L));
        assertThat(invocation.retries()).isEqualTo(7);
        assertThat(invocation.fresh()).isTrue();
    }

    @Test
    void localOptionsOverrideGlobalOptions() throws IOException {
        val capturedInvocation = new AtomicReference<AttributeFinderInvocation>();
        val capturingBroker    = mockCapturingAttributeStreamBroker(capturedInvocation);

        val expression = ParserUtil.expression("""
                <test.attribute[{
                    "initialTimeOutMs": 2000,
                    "retries": 10
                }]>
                """);

        val globalOptions = Val.ofJson("""
                {
                    "attributeFinderOptions": {
                        "initialTimeOutMs": 8000,
                        "pollIntervalMs": 50000,
                        "backoffMs": 3000,
                        "retries": 2,
                        "fresh": true
                    }
                }
                """);

        val evaluationContext = AuthorizationContext.setAttributeStreamBroker(
                AuthorizationContext.setVariables(Context.empty(), Map.of("SAPL", globalOptions)), capturingBroker);

        StepVerifier.create(expression.evaluate().contextWrite(evaluationContext)).expectNextCount(1).verifyComplete();

        val invocation = capturedInvocation.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.initialTimeOut()).isEqualTo(Duration.ofMillis(2000L));
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(50000L));
        assertThat(invocation.backoff()).isEqualTo(Duration.ofMillis(3000L));
        assertThat(invocation.retries()).isEqualTo(10);
        assertThat(invocation.fresh()).isTrue();
    }

    @Test
    void invalidOptionTypesFallBackToDefaults() throws IOException {
        val capturedInvocation = new AtomicReference<AttributeFinderInvocation>();
        val capturingBroker    = mockCapturingAttributeStreamBroker(capturedInvocation);

        val expression = ParserUtil.expression("""
                <test.attribute[{
                    "initialTimeOutMs": "not-a-number",
                    "retries": true,
                    "fresh": 123
                }]>
                """);

        val evaluationContext = AuthorizationContext.setAttributeStreamBroker(
                AuthorizationContext.setVariables(Context.empty(), Map.of()), capturingBroker);

        StepVerifier.create(expression.evaluate().contextWrite(evaluationContext)).expectNextCount(1).verifyComplete();

        val invocation = capturedInvocation.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.initialTimeOut()).isEqualTo(Duration.ofMillis(3000L));
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(30000L));
        assertThat(invocation.backoff()).isEqualTo(Duration.ofMillis(1000L));
        assertThat(invocation.retries()).isEqualTo(3);
        assertThat(invocation.fresh()).isFalse();
    }

    @Test
    void optionsFromVariableReference() throws IOException {
        val capturedInvocation = new AtomicReference<AttributeFinderInvocation>();
        val capturingBroker    = mockCapturingAttributeStreamBroker(capturedInvocation);

        val expression = ParserUtil.expression("<test.attribute[customOptions]>");

        val customOptions = Val.ofJson("""
                {
                    "initialTimeOutMs": 4000,
                    "fresh": true
                }
                """);

        val evaluationContext = AuthorizationContext.setAttributeStreamBroker(
                AuthorizationContext.setVariables(Context.empty(), Map.of("customOptions", customOptions)),
                capturingBroker);

        StepVerifier.create(expression.evaluate().contextWrite(evaluationContext)).expectNextCount(1).verifyComplete();

        val invocation = capturedInvocation.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.initialTimeOut()).isEqualTo(Duration.ofMillis(4000L));
        assertThat(invocation.pollInterval()).isEqualTo(Duration.ofMillis(30000L));
        assertThat(invocation.backoff()).isEqualTo(Duration.ofMillis(1000L));
        assertThat(invocation.retries()).isEqualTo(3);
        assertThat(invocation.fresh()).isTrue();
    }

    private static AttributeStreamBroker mockCapturingAttributeStreamBroker(
            AtomicReference<AttributeFinderInvocation> capturedInvocation) {
        val broker = mock(AttributeStreamBroker.class);
        when(broker.attributeStream(any())).thenAnswer(invocation -> {
            capturedInvocation.set(invocation.getArgument(0));
            return Flux.just(Val.TRUE);
        });
        return broker;
    }
}
