/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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

import static io.sapl.hamcrest.Matchers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;

class VariableContextTests {

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

    private static final Map<String, Val> EMPTY_MAP = new HashMap<>();

    @Test
    void emptyInitializationTest() {
        var ctx = new VariableContext(EMPTY_MAP);
        assertThat(ctx, notNullValue());
    }

    @Test
    void authzSubscriptionInitializationTest() {
        var ctx = new VariableContext(EMPTY_MAP).forAuthorizationSubscription(AUTH_SUBSCRIPTION);
        assertAll(() -> assertThat(ctx, notNullValue()), () -> assertThat(ctx.get("subject"), is(SUBJECT_NODE)),
                () -> assertThat(ctx.get("action"), is(ACTION_NODE)),
                () -> assertThat(ctx.get("resource"), is(RESOURCE_NODE)),
                () -> assertThat(ctx.get("environment"), is(ENVIRONMENT_NODE)));
    }

    @Test
    void emptyAuthzSubscriptionInitializationTest() {
        var ctx = new VariableContext(EMPTY_MAP).forAuthorizationSubscription(EMPTY_AUTH_SUBSCRIPTION);
        assertAll(() -> assertThat(ctx, notNullValue()), () -> assertThat(ctx.get("subject"), is(valUndefined())),
                () -> assertThat(ctx.get("action"), is(valUndefined())),
                () -> assertThat(ctx.get("resource"), is(valUndefined())),
                () -> assertThat(ctx.get("environment"), is(valUndefined())));
    }

    @Test
    void notExistsTest() {
        var ctx = new VariableContext(EMPTY_MAP).forAuthorizationSubscription(AUTH_SUBSCRIPTION);
        assertThat(ctx.exists(VAR_ID), is(false));
    }

    @Test
    void existsTest() {
        var ctx = new VariableContext(EMPTY_MAP);
        ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
        ctx = ctx.withEnvironmentVariable(VAR_ID, VAR_NODE.get());
        assertThat(ctx.get(VAR_ID), is(VAR_NODE));
    }

    @Test
    void doubleRegistrationOverwrite() {
        var ctx = new VariableContext(EMPTY_MAP);
        ctx = ctx.forAuthorizationSubscription(AUTH_SUBSCRIPTION);
        ctx = ctx.withEnvironmentVariable(VAR_ID, VAR_NODE.get());
        ctx = ctx.withEnvironmentVariable(VAR_ID, VAR_NODE_NEW.get());
        assertThat(ctx.get(VAR_ID), is(VAR_NODE_NEW));
    }

    @Test
    void failGetUndefined() {
        var ctx = new VariableContext(EMPTY_MAP).forAuthorizationSubscription(AUTH_SUBSCRIPTION);
        assertThat(ctx.get(VAR_ID), is(Val.UNDEFINED));
    }

    @Test
    void when_getVariables_then_returnsMap() {
        var ctx = new VariableContext(EMPTY_MAP).forAuthorizationSubscription(AUTH_SUBSCRIPTION);
        assertThat(ctx.getVariables().get("action"), is(val(is(ACTION_NODE.get()))));
    }

    @Test
    void when_attemptingToSetReservedVariable_then_raiseException() {
        var ctx = new VariableContext(EMPTY_MAP);
        assertAll(
                () -> assertThrows(PolicyEvaluationException.class,
                        () -> ctx.withEnvironmentVariable("subject", mock(JsonNode.class))),
                () -> assertThrows(PolicyEvaluationException.class,
                        () -> ctx.withEnvironmentVariable("action", mock(JsonNode.class))),
                () -> assertThrows(PolicyEvaluationException.class,
                        () -> ctx.withEnvironmentVariable("resource", mock(JsonNode.class))),
                () -> assertThrows(PolicyEvaluationException.class,
                        () -> ctx.withEnvironmentVariable("environment", mock(JsonNode.class))));
    }

}
