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

import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionErrors;
import static io.sapl.grammar.sapl.impl.util.TestUtil.expressionEvaluatesTo;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.grammar.sapl.impl.util.MockUtil;
import io.sapl.interpreter.EvaluationContext;

public class ApplyStepsConditionTest {

	EvaluationContext ctx = MockUtil.mockEvaluationContext();

	@Test
	public void propagatesErrors() throws IOException {
		expressionErrors(ctx, "(10/0)[?(@>0)]");
	}

	@Test
	public void onUndefinedError() throws IOException {
		expressionErrors(ctx, "undefined[?(@>0)]");
	}

	@Test
	public void nonObjectNonArray() throws IOException {
		expressionErrors(ctx, "\"Toastbrot\"[?(@>0)]");
	}

	@Test
	public void applyToObjectConditionNotBoolean() {
		expressionEvaluatesTo(ctx, "{ \"key\" : null }[?(null)]", "[]");
	}

	@Test
	public void applyToArray() {
		expressionEvaluatesTo(ctx, "[20, 5][?(@>10)]", "[20]");
	};

	@Test
	public void applyToObjectNode() throws JsonProcessingException {
		expressionEvaluatesTo(ctx, "{ \"key1\" : 20, \"key2\" : 5 }[?(@>10)]", "[20]");
	}

}
