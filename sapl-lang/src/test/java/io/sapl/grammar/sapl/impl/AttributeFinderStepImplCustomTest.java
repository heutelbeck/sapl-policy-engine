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

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class AttributeFinderStepImplCustomTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static String ATTRIBUTE = "attribute";
	private static String FULLY_QUALIFIED_ATTRIBUTE = "mock." + ATTRIBUTE;

	private final static EvaluationContext CTX = MockUtil.constructTestEnvironmentPdpScopedEvaluationContext();

	@Test
	public void evaluateBasicAttributeFlux() {
		var expression = "\"\".<test.numbers>";
		var expected = new String[] { "0", "1", "2", "3", "4", "5" };
		expressionEvaluatesTo(CTX, expression, expected);
	}

	@Test
	public void evaluateBasicAttributeInTargetPolicy() throws IOException {
		var expression = ParserUtil.expression("\"\".<test.numbers>");
		MockUtil.mockPolicyTargetExpressionContainerExpression(expression);
		expressionErrors(CTX, expression);
	}

	@Test
	public void evaluateBasicAttributeInTargetPolicySet() throws IOException {
		var expression = ParserUtil.expression("\"\".<test.numbers>");
		MockUtil.mockPolicySetTargetExpressionContainerExpression(expression);
		expressionErrors(CTX, expression);
	}

	@Test
	public void evaluateBasicAttributeOnUndefined() {
		expressionErrors(CTX, "undefined.<test.numbers>");
	}

	@Test
	public void evaluateAttributeInFilterSelction() {
		expressionErrors(CTX, "123 |- { @.<test.numbers> : mock.nil }");
	}

	@Test
	public void exceptionDuringEvaluation() {
		var ctx = mockEvaluationContextWithAttributeStream(Flux.just(Val.error("ERROR")));
		var step = attributeFinderStep();
		StepVerifier.create(step.apply(Val.NULL, ctx, Val.UNDEFINED)).expectNextMatches(Val::isError).verifyComplete();
	}

	@Test
	public void applyWithSomeStreamData() {
		Val[] data = { Val.FALSE, Val.error("ERROR"), Val.TRUE, Val.NULL, Val.UNDEFINED };
		var ctx = mockEvaluationContextWithAttributeStream(Flux.just(data));
		var step = attributeFinderStep();
		StepVerifier.create(step.apply(Val.NULL, ctx, Val.UNDEFINED)).expectNext(data).verifyComplete();
	}

	private static EvaluationContext mockEvaluationContextWithAttributeStream(Flux<Val> stream) {
		var attributeCtx = mock(AttributeContext.class);
		when(attributeCtx.evaluate(eq(FULLY_QUALIFIED_ATTRIBUTE), any(), any(), any())).thenReturn(stream);
		var ctx = mock(EvaluationContext.class);
		when(ctx.getAttributeCtx()).thenReturn(attributeCtx);
		var imports = new HashMap<String, String>();
		imports.put(ATTRIBUTE, FULLY_QUALIFIED_ATTRIBUTE);
		when(ctx.getImports()).thenReturn(imports);
		return ctx;
	}

	private static AttributeFinderStep attributeFinderStep() {
		var step = FACTORY.createAttributeFinderStep();
		step.getIdSteps().add(ATTRIBUTE);
		return step;
	}

}
