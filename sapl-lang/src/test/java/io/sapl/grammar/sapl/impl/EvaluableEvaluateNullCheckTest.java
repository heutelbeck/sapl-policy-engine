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
import io.sapl.grammar.sapl.Evaluable;
import io.sapl.interpreter.EvaluationContext;

@RunWith(Parameterized.class)
public class EvaluableEvaluateNullCheckTest {

	private final static EvaluationContext CTX = mock(EvaluationContext.class);

	private Evaluable evaluable;

	public EvaluableEvaluateNullCheckTest(Evaluable evaluable) {
		this.evaluable = evaluable;
	}

	@Parameters
	public static Collection<Object> data() throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		var reflections = new Reflections("io.sapl.grammar.sapl.impl");
		var classes = reflections.getSubTypesOf(Evaluable.class);
		List<Object> instances = new ArrayList<Object>(classes.size());
		for (var clazz : classes) {
			if (clazz.getSimpleName().endsWith("ImplCustom")
					&& !clazz.getSimpleName().equals("BasicExpressionImplCustom")) {
				instances.add(clazz.getDeclaredConstructor().newInstance());
			}
		}
		return (List<Object>) instances;
	}

	@Test(expected = NullPointerException.class)
	public void nullEvaluationContext() {
		evaluable.evaluate(null, Val.UNDEFINED);
	}

	@Test(expected = NullPointerException.class)
	public void nullullRelativeNode() {
		evaluable.evaluate(CTX, null);
	}
}
