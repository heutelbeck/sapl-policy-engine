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
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.testutil.MockUtil;
import io.sapl.testutil.ParserUtil;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;

import static io.sapl.testutil.TestUtil.assertExpressionErrors;
import static io.sapl.testutil.TestUtil.assertExpressionEvaluatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicEnvironmentHeadAttributeImplTests {

    private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

    private static final String ATTRIBUTE = "attribute";

    private static final String FULLY_QUALIFIED_ATTRIBUTE = "mock." + ATTRIBUTE;

    @Test
    void evaluateBasicAttributeFlux() {
        final var expression = "|<test.numbers>";
        final var expected   = new String[] { "0" };
        assertExpressionEvaluatesTo(expression, expected);
    }

    @Test
    void evaluateBasicAttributeInTargetPolicy() throws IOException {
        final var expression = ParserUtil.expression("|<test.numbers>");
        MockUtil.mockPolicyTargetExpressionContainerExpression(expression);
        assertExpressionErrors(expression);
    }

    @Test
    void evaluateBasicAttributeInTargetPolicySet() throws IOException {
        final var expression = ParserUtil.expression("|<test.numbers>");
        MockUtil.mockPolicySetTargetExpressionContainerExpression(expression);
        assertExpressionErrors(expression);
    }

    @Test
    void exceptionDuringEvaluation() {
        final var step = headAttributeFinderStep();
        final var sut  = step.evaluate().contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx,
                mockAttributeStreamBrokerWithStream(Flux.just(ErrorFactory.error("ERROR")))));
        StepVerifier.create(sut).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void applyWithSomeStreamData() {
        Val[]     data = { Val.FALSE, ErrorFactory.error("ERROR"), Val.TRUE, Val.NULL, Val.UNDEFINED };
        final var step = headAttributeFinderStep();
        final var sut  = step.evaluate().contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx,
                mockAttributeStreamBrokerWithStream(Flux.just(data))));
        StepVerifier.create(sut).expectNext(Val.FALSE).verifyComplete();
    }

    private static AttributeStreamBroker mockAttributeStreamBrokerWithStream(Flux<Val> stream) {
        final var attributeBroker = mock(AttributeStreamBroker.class);
        when(attributeBroker.attributeStream(any())).thenReturn(stream);
        return attributeBroker;
    }

    private static BasicEnvironmentHeadAttribute headAttributeFinderStep() {
        final var step = FACTORY.createBasicEnvironmentHeadAttribute();
        step.eSet(step.eClass().getEStructuralFeature("identifier"), FACTORY.createFunctionIdentifier());
        step.getIdentifier().getNameFragments().add(FULLY_QUALIFIED_ATTRIBUTE);
        return step;
    }

}
