/*
 * Copyright © 2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
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

class MultiAuthorizationSubscriptionTest {

	private static final String SUBSCRIPTION_ID = "SUBSCRIPTION-ID";
	private static final String MULTI_AUTHORIZATION_SUBSCRIPTION = "MultiAuthorizationSubscription";
	private static final String ID = "ID";
	private static final String ID2 = "ID2";
	private static final String RESOURCE = "RESOURCE";
	private static final String RESOURCE2 = "RESOURCE2";
	private static final String ACTION = "ACTION";
	private static final String SUBJECT = "SUBJECT";

	@Test
	void defaultConstructorTest() {
		var subscription = new MultiAuthorizationSubscription();
		assertAll(() -> assertThat(subscription.getSubjects(), is(empty())),
				() -> assertThat(subscription.getActions(), is(empty())),
				() -> assertThat(subscription.getResources(), is(empty())),
				() -> assertThat(subscription.getEnvironments(), is(empty())),
				() -> assertThat(subscription.getAuthorizationSubscriptions(), is(anEmptyMap())));
	}

	@Test
	void falseHasAuthorizationSubscriptionTest() {
		var subscription = new MultiAuthorizationSubscription();
		assertFalse(subscription.hasAuthorizationSubscriptions());
	}

	@Test
	void trueHasAuthorizationSubscriptionTest() {
		var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION,
				RESOURCE);
		assertTrue(subscription.hasAuthorizationSubscriptions());
	}

	@Test
	void addNullIdFailsTest() {
		assertThrows(NullPointerException.class, () -> {
			new MultiAuthorizationSubscription().addAuthorizationSubscription(null, SUBJECT, ACTION, RESOURCE);
		});
	}

	@Test
	void emptySubscriptionToStringTest() {
		var string = new MultiAuthorizationSubscription().toString();
		assertAll(() -> assertThat(string, startsWith(MULTI_AUTHORIZATION_SUBSCRIPTION)),
				() -> assertThat(string, not(containsString(SUBSCRIPTION_ID))));
	}

	@Test
	void filledSubscriptionToStringTest() {
		var string = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE)
				.toString();
		assertAll(() -> assertThat(string, startsWith(MULTI_AUTHORIZATION_SUBSCRIPTION)),
				() -> assertThat(string, containsString(SUBSCRIPTION_ID + ": " + ID)));
	}

	@Test
	void addTwoSubscriptionsTest() {
		var subscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE)
				.addAuthorizationSubscription(ID2, SUBJECT, ACTION, RESOURCE2);
		assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getResource(),
						is(jsonText(RESOURCE))),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2), notNullValue()),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2).getResource(),
						is(jsonText(RESOURCE2))));
	}

	@Test
	void addTwoSubscriptionsViaBasicSubscriptionTest() {
		var subscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription(ID, AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE))
				.addAuthorizationSubscription(ID2, AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE2));
		assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getResource(),
						is(jsonText(RESOURCE))),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2), notNullValue()),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID2).getResource(),
						is(jsonText(RESOURCE2))));
	}
	@Test
	void addSameIdTwiceFailsTest() {
		assertThrows(IllegalArgumentException.class, () -> {
			new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE)
					.addAuthorizationSubscription(ID, SUBJECT, ACTION, RESOURCE2);
		});
	}

	@Test
	void getExistingSubscriptionByIdTest() {
		var subscription = new MultiAuthorizationSubscription().addAuthorizationSubscription(ID, SUBJECT, ACTION,
				RESOURCE);
		assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID), notNullValue()),
				() -> assertThat(subscription.getAuthorizationSubscriptionWithId(ID).getAction(),
						is(jsonText(ACTION))));
	}

	@Test
	void getNonExistingSubscriptionByIdTest() {
		var subscription = new MultiAuthorizationSubscription();
		assertThat(subscription.getAuthorizationSubscriptionWithId(ID), nullValue());
	}

}
