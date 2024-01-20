/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.testutil.TestUtil.assertExpressionErrors;
import static io.sapl.testutil.TestUtil.assertExpressionReturnsError;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.testutil.MockUtil;
import io.sapl.testutil.ParserUtil;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AttributeFinderStepImplCustomTests {

    private static final SaplFactory FACTORY                   = SaplFactoryImpl.eINSTANCE;
    private static final String      ATTRIBUTE                 = "attribute";
    private static final String      FULLY_QUALIFIED_ATTRIBUTE = "mock." + ATTRIBUTE;

    private static Stream<Arguments> errorExpressions() {
        // @formatter:off
		return Stream.of(
	 			// errorPropagates
	 			Arguments.of("(1/0).<test.numbers>", "Division by zero"),

	 			// evaluateBasicAttributeOnUndefined
	 			Arguments.of("undefined.<test.numbers>", "Undefined value handed over as left-hand parameter to policy information point"),

	 			// evaluateAttributeInFilterSelection
	 			Arguments.of("123 |- { @.<test.numbers> : mock.nil }", "AttributeFinderStep not permitted in filter selection steps.")
	 		);
		// @formater:on
	}

	@ParameterizedTest
	@MethodSource("errorExpressions")
	void expressionReturnsError(String expression, String expected) {
		assertExpressionReturnsError(expression, expected);
	}

	@Test
	void evaluateBasicAttributeInTargetPolicy() throws IOException {
		var expression = ParserUtil.expression("\"\".<test.numbers>");
		MockUtil.mockPolicyTargetExpressionContainerExpression(expression);
		assertExpressionErrors(expression);
	}

	@Test
	void evaluateBasicAttributeInTargetPolicySet() throws IOException {
		var expression = ParserUtil.expression("\"\".<test.numbers>");
		MockUtil.mockPolicySetTargetExpressionContainerExpression(expression);
		assertExpressionErrors(expression);
	}

	@Test
	void exceptionDuringEvaluation() {
		var step = attributeFinderStep();
		var sut  = step.apply(Val.NULL).contextWrite(
				ctx -> AuthorizationContext.setAttributeContext(ctx,
						mockAttributeContext(Flux.just(Val.error("ERROR")))));
		StepVerifier.create(sut).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void applyWithSomeStreamData() {
		Val[] data = { Val.FALSE, Val.error("ERROR"), Val.TRUE, Val.NULL, Val.UNDEFINED };
		var   step = attributeFinderStep();
		var   sut  = step.apply(Val.NULL).contextWrite(
				ctx -> AuthorizationContext.setAttributeContext(ctx, mockAttributeContext(Flux.just(data))));
		StepVerifier.create(sut).expectNext(data).verifyComplete();
	}

	private static AttributeContext mockAttributeContext(Flux<Val> stream) {
		var attributeCtx = mock(AttributeContext.class);
		when(attributeCtx.evaluateAttribute(eq(FULLY_QUALIFIED_ATTRIBUTE), any(), any(), any())).thenReturn(stream);
		return attributeCtx;
	}

	private static AttributeFinderStep attributeFinderStep() {
		var step = FACTORY.createAttributeFinderStep();
		step.getIdSteps().add(FULLY_QUALIFIED_ATTRIBUTE);
		return step;
	}

}
