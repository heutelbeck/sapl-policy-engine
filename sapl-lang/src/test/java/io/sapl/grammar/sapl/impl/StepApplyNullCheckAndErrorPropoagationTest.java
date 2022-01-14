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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reflections.Reflections;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.Step;
import reactor.test.StepVerifier;

class StepApplyNullCheckAndErrorPropoagationTest {

	static Collection<Step> data()
			throws InstantiationException,
				IllegalAccessException,
				IllegalArgumentException,
				InvocationTargetException,
				NoSuchMethodException,
				SecurityException {
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
	void nullRelativeNode(Step step) {
		assertThrows(NullPointerException.class, () -> step.apply(Val.UNDEFINED, null));
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullParentNode(Step step) {
		assertThrows(NullPointerException.class, () -> step.apply(null, Val.UNDEFINED));
	}

	@ParameterizedTest
	@MethodSource("data")
	void stepsPropagateErrors(Step step) {
		var error = Val.error("TEST");
		StepVerifier.create(step.apply(error, Val.UNDEFINED)).expectNext(error).verifyComplete();
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullRelativeNodeFilter(Step step) {
		assertThrows(NullPointerException.class,
				() -> step.applyFilterStatement(Val.UNDEFINED, null, 0, mock(FilterStatement.class)));
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullParentNodeFilter(Step step) {
		assertThrows(NullPointerException.class,
				() -> step.applyFilterStatement(null, Val.UNDEFINED, 0, mock(FilterStatement.class)));
	}

	@ParameterizedTest
	@MethodSource("data")
	void nullFilterStatementFilter(Step step) {
		assertThrows(NullPointerException.class,
				() -> step.applyFilterStatement(Val.UNDEFINED, Val.UNDEFINED, 0, null));
	}

}
