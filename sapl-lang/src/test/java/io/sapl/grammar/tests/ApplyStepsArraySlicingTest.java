/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.tests;

import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ArraySlicingStep;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.util.ArrayUtil;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

public class ApplyStepsArraySlicingTest {

	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;
	private static EvaluationContext CTX = mock(EvaluationContext.class);

	private static Val ZERO_TO_NINE = ArrayUtil.numberArrayRange(0, 9);

	@Test
	public void slicingPropagatesErrors() {
		ArraySlicingStep slicingStep = FACTORY.createArraySlicingStep();
		StepVerifier.create(slicingStep.apply(Val.error(""), CTX, Val.UNDEFINED)).expectNext(Val.error(""))
				.verifyComplete();
	}

	@Test
	public void applySlicingToNoArray() {
		ArraySlicingStep slicingStep = FACTORY.createArraySlicingStep();
		StepVerifier.create(slicingStep.apply(Val.ofNull(), CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void defaultsToIdentity() {
		var slicingStep = slicingStep(null, null, null);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(ZERO_TO_NINE)
				.verifyComplete();
	}

	@Test
	public void useCaseTestTwoNull() {
		var slicingStep = slicingStep(BigDecimal.valueOf(2), null, null);
		var expectedResult = ArrayUtil.numberArrayRange(3, 5);
		StepVerifier.create(slicingStep.apply(ArrayUtil.numberArrayRange(1, 5), CTX, Val.UNDEFINED))
				.expectNext(expectedResult).verifyComplete();
	}

	@Test
	public void negativeToTest() {
		var slicingStep = slicingStep(BigDecimal.valueOf(7), BigDecimal.valueOf(-1), null);
		var expectedResult = ArrayUtil.numberArrayRange(7, 8);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeNegativeFrom() {
		var slicingStep = slicingStep(BigDecimal.valueOf(-3), BigDecimal.valueOf(9), null);
		var expectedResult = ArrayUtil.numberArrayRange(7, 8);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayWithFromGreaterThanToReturnsEmptyArray() {
		var slicingStep = slicingStep(BigDecimal.valueOf(4), BigDecimal.valueOf(1), null);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(Val.ofEmptyArray())
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithoutTo() {
		var slicingStep = slicingStep(BigDecimal.valueOf(7), null, null);
		var expectedResult = ArrayUtil.numberArrayRange(7, 9);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithoutFrom() {
		var slicingStep = slicingStep(null, BigDecimal.valueOf(3), null);
		var expectedResult = ArrayUtil.numberArrayRange(0, 2);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeFrom() {
		var slicingStep = slicingStep(BigDecimal.valueOf(-3), null, null);
		var expectedResult = ArrayUtil.numberArrayRange(7, 9);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStep() {
		var slicingStep = slicingStep(null, null, BigDecimal.valueOf(-1));
		var expectedResult = ArrayUtil.numberArrayRange(0, 9);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFrom() {
		var slicingStep = slicingStep(BigDecimal.valueOf(-2), BigDecimal.valueOf(6), BigDecimal.valueOf(-1));
		var expectedResult = Val.ofEmptyArray();
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayNodeWithNegativeStepAndNegativeFromAndTo() {
		var slicingStep = slicingStep(BigDecimal.valueOf(-2), BigDecimal.valueOf(-5), BigDecimal.valueOf(-1));
		var expectedResult = Val.ofEmptyArray();
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingToArrayWithNegativeStepAndToGreaterThanFrom() {
		var slicingStep = slicingStep(BigDecimal.valueOf(1), BigDecimal.valueOf(5), BigDecimal.valueOf(-1));
		var expectedResult = ArrayUtil.numberArrayRange(1, 4);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	@Test
	public void applySlicingStepZeroErrors() {
		var slicingStep = slicingStep(BigDecimal.valueOf(1), BigDecimal.valueOf(5), BigDecimal.valueOf(0));
		StepVerifier.create(slicingStep.apply(Val.ofNull(), CTX, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void applySlicingToResultArray() {
		var slicingStep = slicingStep(BigDecimal.valueOf(3), BigDecimal.valueOf(6), null);
		var expectedResult = ArrayUtil.numberArrayRange(3, 5);
		StepVerifier.create(slicingStep.apply(ZERO_TO_NINE, CTX, Val.UNDEFINED)).expectNext(expectedResult)
				.verifyComplete();
	}

	private ArraySlicingStep slicingStep(BigDecimal start, BigDecimal end, BigDecimal step) {
		ArraySlicingStep slicingStep = FACTORY.createArraySlicingStep();
		slicingStep.setIndex(start);
		slicingStep.setStep(step);
		slicingStep.setTo(end);
		return slicingStep;
	}

}
