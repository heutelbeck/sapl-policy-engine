/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.assertExpressionEvaluatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class BasicEnvironmentHeadAttributeImplTests {

    private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

    private static final String ATTRIBUTE = "attribute";

    private static final String FULLY_QUALIFIED_ATTRIBUTE = "mock." + ATTRIBUTE;

    @Test
    void evaluateBasicAttributeFlux() {
        var expression = "|<test.numbers>";
        var expected   = new String[] { "0" };
        assertExpressionEvaluatesTo(expression, expected);
    }

    @Test
    void evaluateBasicAttributeInTargetPolicy() throws IOException {
        var expression = ParserUtil.expression("|<test.numbers>");
        MockUtil.mockPolicyTargetExpressionContainerExpression(expression);
        assertExpressionErrors(expression);
    }

    @Test
    void evaluateBasicAttributeInTargetPolicySet() throws IOException {
        var expression = ParserUtil.expression("|<test.numbers>");
        MockUtil.mockPolicySetTargetExpressionContainerExpression(expression);
        assertExpressionErrors(expression);
    }

    @Test
    void exceptionDuringEvaluation() {
        var step = headAttributeFinderStep();
        var sut  = step.evaluate().contextWrite(ctx -> AuthorizationContext.setAttributeContext(ctx,
                mockAttributeContextWithStream(Flux.just(Val.error("ERROR")))));
        StepVerifier.create(sut).expectNextMatches(Val::isError).verifyComplete();
    }

    @Test
    void applyWithSomeStreamData() {
        Val[] data = { Val.FALSE, Val.error("ERROR"), Val.TRUE, Val.NULL, Val.UNDEFINED };
        var   step = headAttributeFinderStep();
        var   sut  = step.evaluate().contextWrite(
                ctx -> AuthorizationContext.setAttributeContext(ctx, mockAttributeContextWithStream(Flux.just(data))));
        StepVerifier.create(sut).expectNext(Val.FALSE).verifyComplete();
    }

    private static AttributeContext mockAttributeContextWithStream(Flux<Val> stream) {
        var attributeCtx = mock(AttributeContext.class);
        when(attributeCtx.evaluateAttribute(eq(FULLY_QUALIFIED_ATTRIBUTE), any(), any(), any())).thenReturn(stream);
        when(attributeCtx.evaluateEnvironmentAttribute(eq(FULLY_QUALIFIED_ATTRIBUTE), any(), any())).thenReturn(stream);
        return attributeCtx;
    }

    private static BasicEnvironmentHeadAttribute headAttributeFinderStep() {
        var step = FACTORY.createBasicEnvironmentHeadAttribute();
        step.getIdSteps().add(FULLY_QUALIFIED_ATTRIBUTE);
        return step;
    }

}
