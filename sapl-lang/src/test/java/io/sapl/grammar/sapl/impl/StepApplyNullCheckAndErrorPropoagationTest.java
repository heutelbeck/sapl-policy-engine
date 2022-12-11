/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reflections.Reflections;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ConditionStep;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.RecursiveIndexStep;
import io.sapl.grammar.sapl.Step;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import reactor.test.StepVerifier;

class StepApplyNullCheckAndErrorPropoagationTest {

	static Collection<Step> data() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		var        reflections = new Reflections("io.sapl.grammar.sapl.impl");
		var        classes     = reflections.getSubTypesOf(Step.class);
		List<Step> instances   = new ArrayList<>(classes.size());
		for (var clazz : classes) {
			if (clazz.getSimpleName().endsWith("ImplCustom")) {
				instances.add(clazz.getDeclaredConstructor().newInstance());
			}
		}
		return instances;
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullParentNode(Step step) {
		assertThrows(NullPointerException.class, () -> step.apply(null));
	}

	@ParameterizedTest
	@MethodSource("data")
	void stepsPropagateErrors(Step step) throws IOException {
		var error = Val.error("TEST");
		if (step instanceof ConditionStep) {
			// Special case. this expression checks for expression first and that is just Ok
			((ConditionStep) step).setExpression(ParserUtil.expression("true"));
		}
		if (step instanceof RecursiveIndexStep) {
			// Special case. this expression checks for index first and that is just Ok
			((RecursiveIndexStep) step).setIndex(BigDecimal.ONE);
		}
		StepVerifier.create(step.apply(error)).expectNext(error).verifyComplete();
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullParentNodeFilter(Step step) {
		assertThrows(NullPointerException.class, () -> step.applyFilterStatement(null, 0, mock(FilterStatement.class)));
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullFilterStatementFilter(Step step) {
		assertThrows(NullPointerException.class, () -> step.applyFilterStatement(Val.UNDEFINED, 0, null));
	}

}
