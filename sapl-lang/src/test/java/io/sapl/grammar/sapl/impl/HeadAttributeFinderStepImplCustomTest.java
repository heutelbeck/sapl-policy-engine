/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class HeadAttributeFinderStepImplCustomTest {

	private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

	private static final String ATTRIBUTE = "attribute";

	private static final String FULLY_QUALIFIED_ATTRIBUTE = "mock." + ATTRIBUTE;

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	void evaluateBasicAttributeFlux() {
		var expression = "\"\".|<test.numbers>";
		var expected = new String[] { "0" };
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	void evaluateBasicAttributeInTargetPolicy() throws IOException {
		var expression = ParserUtil.expression("\"\".|<test.numbers>");
		MockUtil.mockPolicyTargetExpressionContainerExpression(expression);
		expressionErrors(CTX, expression);
	}

	@Test
	void evaluateBasicAttributeInTargetPolicySet() throws IOException {
		var expression = ParserUtil.expression("\"\".|<test.numbers>");
		MockUtil.mockPolicySetTargetExpressionContainerExpression(expression);
		expressionErrors(CTX, expression);
	}

	@Test
	void evaluateBasicAttributeOnUndefined() {
		expressionErrors(CTX, "undefined.|<test.numbers>");
	}

	@Test
	void evaluateAttributeInFilterSelction() {
		expressionErrors(CTX, "123 |- { @.|<test.numbers> : mock.nil }");
	}

	@Test
	void exceptionDuringEvaluation() {
		var ctx = mockEvaluationContextWithAttributeStream(Flux.just(Val.error("ERROR")));
		var step = headAttributeFinderStep();
		StepVerifier.create(step.apply(Val.NULL, ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	void applyWithSomeStreamData() {
		Val[] data = { Val.FALSE, Val.error("ERROR"), Val.TRUE, Val.NULL, Val.UNDEFINED };
		var ctx = mockEvaluationContextWithAttributeStream(Flux.just(data));
		var step = headAttributeFinderStep();
		StepVerifier.create(step.apply(Val.NULL, ctx, Val.UNDEFINED)).expectNext(Val.FALSE).verifyComplete();
	}

	private static EvaluationContext mockEvaluationContextWithAttributeStream(Flux<Val> stream) {
		var attributeCtx = mock(AttributeContext.class);
		when(attributeCtx.evaluateAttribute(eq(FULLY_QUALIFIED_ATTRIBUTE), any(), any(), any())).thenReturn(stream);
		var ctx = mock(EvaluationContext.class);
		when(ctx.getAttributeCtx()).thenReturn(attributeCtx);
		var imports = new HashMap<String, String>();
		imports.put(ATTRIBUTE, FULLY_QUALIFIED_ATTRIBUTE);
		when(ctx.getImports()).thenReturn(imports);
		return ctx;
	}

	private static HeadAttributeFinderStep headAttributeFinderStep() {
		var step = FACTORY.createHeadAttributeFinderStep();
		step.getIdSteps().add(ATTRIBUTE);
		return step;
	}

}
