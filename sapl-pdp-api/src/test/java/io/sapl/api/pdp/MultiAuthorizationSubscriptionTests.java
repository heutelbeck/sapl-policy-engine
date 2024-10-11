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
package io.sapl.api.pdp;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class MultiAuthorizationSubscriptionTests {

    private static final String          SUBSCRIPTION_ID                  = "SUBSCRIPTION-ID";
    private static final String          MULTI_AUTHORIZATION_SUBSCRIPTION = "MultiAuthorizationSubscription";
    private static final String          ID                               = "ID";
    private static final String          ID2                              = "ID2";
    private static final JsonNodeFactory JSON                             = JsonNodeFactory.instance;
    private static final JsonNode        RESOURCE                         = JSON.textNode("RESOURCE");
    private static final JsonNode        RESOURCE2                        = JSON.textNode("RESOURCE2");
    private static final JsonNode        ACTION                           = JSON.textNode("ACTION");
    private static final JsonNode        SUBJECT                          = JSON.textNode("SUBJECT");
    private static final JsonNode        ENVIRONMENT                      = JSON.textNode("ENVIRONMENT");

    @Test
    void defaultConstructorTest() {
        final var subscription = new MultiAuthorizationSubscription();
        assertAll(() -> assertThat(subscription.getSubjects(), is(empty())),
                () -> assertThat(subscription.getActions(), is(empty())),
                () -> assertThat(subscription.getResources(), is(empty())),
                () -> assertThat(subscription.getEnvironments(), is(empty())),
                () -> assertThat(subscription.getAuthorizationSubscriptions(), is(anEmptyMap())));
    }

    @Test
    void falseHasAuthorizationSubscriptionTest() {
        final var subscription = new MultiAuthorizationSubscription();
        assertFalse(subscription.hasAuthorizationSubscriptions());
    }

    @Test
    void trueHasAuthorizationSubscriptionTest() {
        final var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION,
                RESOURCE);
        assertTrue(subscription.hasAuthorizationSubscriptions());
    }

    @Test
    void addNullIdFailsTest() {
        final var sub1 = new MultiAuthorizationSubscription();
        assertThrows(NullPointerException.class,
                () -> sub1.addAuthorizationSubscription(null, SUBJECT, ACTION, RESOURCE));
        final var sub2 = new MultiAuthorizationSubscription();
        assertThrows(NullPointerException.class, () -> sub2.addAuthorizationSubscription(ID, null, ACTION, RESOURCE));
        final var sub3 = new MultiAuthorizationSubscription();
        assertThrows(NullPointerException.class, () -> sub3.addAuthorizationSubscription(ID, SUBJECT, null, RESOURCE));
        final var sub4 = new MultiAuthorizationSubscription();
        assertThrows(NullPointerException.class, () -> sub4.addAuthorizationSubscription(ID, SUBJECT, ACTION, null));
    }

    @Test
    void emptySubscriptionToStringTest() {
        final var string = new MultiAuthorizationSubscription().toString();
        assertAll(() -> assertThat(string, startsWith(MULTI_AUTHORIZATION_SUBSCRIPTION)),
                () -> assertThat(string, not(containsString(SUBSCRIPTION_ID))));
    }

    @Test
    void filledSubscriptionToStringTest() {
        final var string = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE).toString();
        assertAll(() -> assertThat(string, startsWith(MULTI_AUTHORIZATION_SUBSCRIPTION)),
                () -> assertThat(string, containsString(SUBSCRIPTION_ID + ": " + ID)));
    }

    @Test
    void addTwoSubscriptionsTest() {
        final var subscription = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE)
                .addAuthorizationSubscription(ID2, SUBJECT, ACTION, RESOURCE2);
        assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getResource(), is(RESOURCE)),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2).getResource(), is(RESOURCE2)));
    }

    @Test
    void addTwoSubscriptionsViaBasicSubscriptionTest() {
        final var subscription = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription(ID, AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE))
                .addAuthorizationSubscription(ID2, AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE2));
        assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getResource(), is(RESOURCE)),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2).getResource(), is(RESOURCE2)));
    }

    @Test
    void addTwoSubscriptionsViaBasicSubscriptionWithNonNullEnvironmentTest() {
        final var subscription = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription(ID, AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE, ENVIRONMENT))
                .addAuthorizationSubscription(ID2,
                        AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE2, ENVIRONMENT));
        assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getResource(), is(RESOURCE)),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2).getResource(), is(RESOURCE2)),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getEnvironment(), is(ENVIRONMENT)),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2).getEnvironment(),
                        is(ENVIRONMENT)));
    }

    @Test
    void addSameIdTwiceFailsTest() {
        final var initialSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT,
                ACTION, RESOURCE);
        assertThrows(IllegalArgumentException.class,
                () -> initialSubscription.addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE2));
    }

    @Test
    void getExistingSubscriptionByIdTest() {
        final var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION,
                RESOURCE);
        assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
                () -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getAction(), is(ACTION)));
    }

    @Test
    void getNonExistingSubscriptionByIdTest() {
        final var subscription = new MultiAuthorizationSubscription();
        assertThat(subscription.getAuthorizationSubscriptionWithId(ID), nullValue());
    }

}
