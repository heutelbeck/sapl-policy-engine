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

import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.reflections.Reflections;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import reactor.test.StepVerifier;

@RunWith(Parameterized.class)
public class StepApplyNullCheckAndErrorPropoagationTest {

	private final static EvaluationContext CTX = mock(EvaluationContext.class);

	private Step step;

	public StepApplyNullCheckAndErrorPropoagationTest(Step step) {
		this.step = step;
	}

	@Parameters
	public static Collection<Object> data() throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		var reflections = new Reflections("io.sapl.grammar.sapl.impl");
		var classes = reflections.getSubTypesOf(Step.class);
		List<Object> instances = new ArrayList<Object>(classes.size());
		for (var clazz : classes) {
			if (clazz.getSimpleName().endsWith("ImplCustom")) {
				instances.add(clazz.getDeclaredConstructor().newInstance());
			}
		}
		return (List<Object>) instances;
	}

	@Test(expected = NullPointerException.class)
	public void nullEvaluationContext() {
		step.apply(Val.UNDEFINED, null, Val.UNDEFINED);
	}

	@Test(expected = NullPointerException.class)
	public void nullRelativeNode() {
		step.apply(Val.UNDEFINED, CTX, null);
	}

	@Test(expected = NullPointerException.class)
	public void nullParentNode() {
		step.apply(null, CTX, Val.UNDEFINED);
	}

	@Test
	public void stepsPropagateErrors() {
		var error = Val.error("TEST");
		StepVerifier.create(step.apply(error, CTX, Val.UNDEFINED)).expectNext(error).verifyComplete();
	}

	@Test(expected = NullPointerException.class)
	public void nullEvaluationContextFilter() {
		step.applyFilterStatement(Val.UNDEFINED, null, Val.UNDEFINED, 0, mock(FilterStatement.class));
	}

	@Test(expected = NullPointerException.class)
	public void nullRelativeNodeFilter() {
		step.applyFilterStatement(Val.UNDEFINED, CTX, null, 0, mock(FilterStatement.class));
	}

	@Test(expected = NullPointerException.class)
	public void nullParentNodeFilter() {
		step.applyFilterStatement(null, CTX, Val.UNDEFINED, 0, mock(FilterStatement.class));
	}

	@Test(expected = NullPointerException.class)
	public void nullFilterStatementFilter() {
		step.applyFilterStatement(Val.UNDEFINED, CTX, Val.UNDEFINED, 0, null);
	}
}
