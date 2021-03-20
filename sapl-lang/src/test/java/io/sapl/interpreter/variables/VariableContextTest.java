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
package io.sapl.interpreter.variables;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;

class VariableContextTest {

	private static final Val SUBJECT_NODE = Val.of("subject");
	private static final Val ACTION_NODE = Val.of("action");
	private static final Val RESOURCE_NODE = Val.of("resource");
	private static final Val ENVIRONMENT_NODE = Val.of("environment");
	private static final Val VAR_NODE = Val.of("var");
	private static final Val VAR_NODE_NEW = Val.of("var_new");
	private static final String VAR_ID = "var";

	private static final AuthorizationSubscription AUTH_SUBSCRIPTION = new AuthorizationSubscription(SUBJECT_NODE.get(),
			ACTION_NODE.get(), RESOURCE_NODE.get(), ENVIRONMENT_NODE.get());

	private static final AuthorizationSubscription EMPTY_AUTH_SUBSCRIPTION = new AuthorizationSubscription(null, null,
			null, null);
	private static final Map<String, JsonNode> EMPTY_MAP = new HashMap<>();

	@Test
	void emtpyInitializationTest() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		assertThat(ctx, not(nullValue()));
	}

	@Test
	void authzSubscriptionInitializationTest() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
		assertTrue(ctx != null && ctx.get("subject").equals(SUBJECT_NODE) && ctx.get("action").equals(ACTION_NODE)
				&& ctx.get("resource").equals(RESOURCE_NODE) && ctx.get("environment").equals(ENVIRONMENT_NODE));
	}

	@Test
	void emptyauthzSubscriptionInitializationTest() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		ctx = ctx.forAuthorizationSubscription(EMPTY_AUTH_SUBSCRIPTION);
		assertTrue(ctx != null && ctx.get("subject").equals(Val.NULL) && ctx.get("action").equals(Val.NULL)
				&& ctx.get("resource").equals(Val.NULL) && ctx.get("environment").equals(Val.NULL));
	}

	@Test
	void notExistsTest() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
		assertFalse(ctx.exists(VAR_ID));
	}

	@Test
	void existsTest() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
		ctx = ctx.withEnvironmentVariable(VAR_ID, VAR_NODE.get());
		assertEquals(ctx.get(VAR_ID), VAR_NODE);
	}

	@Test
	void doubleRegistrationOverwrite() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
		ctx = ctx.withEnvironmentVariable(VAR_ID, VAR_NODE.get());
		ctx = ctx.withEnvironmentVariable(VAR_ID, VAR_NODE_NEW.get());
		assertEquals(ctx.get(VAR_ID), VAR_NODE_NEW);
	}

	@Test
	void failGetUndefined() {
		VariableContext ctx = new VariableContext(EMPTY_MAP);
		ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
		assertThat(ctx.get(VAR_ID), is(Val.UNDEFINED));
	}

}
