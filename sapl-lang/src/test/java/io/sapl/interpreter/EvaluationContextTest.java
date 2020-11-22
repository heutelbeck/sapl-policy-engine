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
package io.sapl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.junit.Test;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

public class EvaluationContextTest {

	@Test
	public void okCase() {
		assertThat(new EvaluationContext(mock(AttributeContext.class), mock(FunctionContext.class),
				Collections.emptyMap())).isNotNull();
	}

	@Test(expected = NullPointerException.class)
	public void nullAttributeCtxRejected() {
		new EvaluationContext(null, mock(FunctionContext.class), Collections.emptyMap());
	}

	@Test(expected = NullPointerException.class)
	public void nullAttributeCtxRejected2() {
		new EvaluationContext(null, null, Collections.emptyMap());
	}

	@Test(expected = NullPointerException.class)
	public void nullFunctionCtxRejected() {
		new EvaluationContext(mock(AttributeContext.class), null, Collections.emptyMap());
	}

	@Test(expected = NullPointerException.class)
	public void nullMapRejected1() {
		new EvaluationContext(mock(AttributeContext.class), mock(FunctionContext.class), null);
	}

	@Test(expected = NullPointerException.class)
	public void nullMapRejected2() {
		new EvaluationContext(mock(AttributeContext.class), null, null);
	}

	@Test(expected = NullPointerException.class)
	public void nullMapRejected3() {
		new EvaluationContext(null, null, null);
	}

	@Test(expected = NullPointerException.class)
	public void nullMapRejected4() {
		new EvaluationContext(null, mock(FunctionContext.class), null);
	}

}
