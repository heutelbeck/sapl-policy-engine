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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;

class EvaluationContextTest {

	@Test
	void okCase() {
		assertThat(new EvaluationContext(mock(AttributeContext.class), mock(FunctionContext.class),
				Collections.emptyMap()), is(notNullValue()));
	}

	@Test
	void nullAttributeCtxRejected() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(null, mock(FunctionContext.class), Collections.emptyMap());
		});
	}

	@Test
	void nullAttributeCtxRejected2() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(null, null, Collections.emptyMap());
		});
	}

	@Test
	void nullFunctionCtxRejected() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(mock(AttributeContext.class), null, Collections.emptyMap());
		});
	}

	@Test
	void nullMapRejected1() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(mock(AttributeContext.class), mock(FunctionContext.class), null);
		});
	}

	@Test
	void nullMapRejected2() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(mock(AttributeContext.class), null, null);
		});
	}

	@Test
	void nullMapRejected3() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(null, null, null);
		});
	}

	@Test
	void nullMapRejected4() {
		assertThrows(NullPointerException.class, () -> {
			new EvaluationContext(null, mock(FunctionContext.class), null);
		});
	}

}
