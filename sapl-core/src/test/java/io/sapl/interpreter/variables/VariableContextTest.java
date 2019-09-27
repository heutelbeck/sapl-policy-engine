/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
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
package io.sapl.interpreter.variables;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Request;

public class VariableContextTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final JsonNode SUBJECT_NODE = JSON.textNode("subject");

	private static final JsonNode ACTION_NODE = JSON.textNode("action");

	private static final JsonNode RESOURCE_NODE = JSON.textNode("resource");

	private static final JsonNode ENVIRONMENT_NODE = JSON.textNode("environment");

	private static final JsonNode NULL_NODE = JSON.nullNode();

	private static final JsonNode VAR_NODE = JSON.textNode("var");

	private static final JsonNode VAR_NODE_NEW = JSON.textNode("var_new");

	private static final String VAR_ID = "var";

	private static final Request REQUEST_OBJECT = new Request(SUBJECT_NODE, ACTION_NODE, RESOURCE_NODE,
			ENVIRONMENT_NODE);

	private static final Request EMPTY_REQUEST_OBJECT = new Request(null, null, null, null);

	@Test
	public void emtpyInitializationTest() {
		VariableContext ctx = new VariableContext();
		assertThat("context was not created and is null", ctx, not(nullValue()));
	}

	@Test
	public void requestInitializationTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(REQUEST_OBJECT);
		assertTrue("context was not created or did not remember values",
				ctx != null && ctx.get("subject").equals(SUBJECT_NODE) && ctx.get("action").equals(ACTION_NODE)
						&& ctx.get("resource").equals(RESOURCE_NODE)
						&& ctx.get("environment").equals(ENVIRONMENT_NODE));
	}

	@Test
	public void emptyRequestInitializationTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(EMPTY_REQUEST_OBJECT);
		assertTrue("context was not created or did not remember values",
				ctx != null && ctx.get("subject").equals(NULL_NODE) && ctx.get("action").equals(NULL_NODE)
						&& ctx.get("resource").equals(NULL_NODE) && ctx.get("environment").equals(NULL_NODE));
	}

	@Test
	public void notExistsTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(REQUEST_OBJECT);
		assertFalse("var should not be existing in freshly created context", ctx.exists(VAR_ID));
	}

	@Test
	public void existsTest() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(REQUEST_OBJECT);
		ctx.put(VAR_ID, VAR_NODE);
		assertTrue("var should be existing in freshly created context", ctx.get(VAR_ID).equals(VAR_NODE));
	}

	@Test
	public void doubleRegistrationOverwrite() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(REQUEST_OBJECT);
		ctx.put(VAR_ID, VAR_NODE);
		ctx.put(VAR_ID, VAR_NODE_NEW);
		assertTrue("", ctx.get(VAR_ID).equals(VAR_NODE_NEW));
	}

	@Test(expected = PolicyEvaluationException.class)
	public void failGetUndefined() throws PolicyEvaluationException {
		VariableContext ctx = new VariableContext(REQUEST_OBJECT);
		ctx.get(VAR_ID);
	}

}
