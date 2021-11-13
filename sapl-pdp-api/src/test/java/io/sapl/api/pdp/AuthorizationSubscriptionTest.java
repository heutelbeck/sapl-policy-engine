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

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonNull;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class AuthorizationSubscriptionTest {

	private static final String ENVIRONMENT = "ENVIRONMENT";

	private static final String RESOURCE = "RESOURCE";

	private static final String ACTION = "ACTION";

	private static final String SUBJECT = "SUBJECT";

	@Test
	void subjectActionResourceDefaultMapper() {
		var subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE);
		assertAll(() -> assertThat(subscription.getSubject(), is(jsonText(SUBJECT))),
				() -> assertThat(subscription.getAction(), is(jsonText(ACTION))),
				() -> assertThat(subscription.getResource(), is(jsonText(RESOURCE))),
				() -> assertThat(subscription.getEnvironment(), is(jsonNull())));
	}

	@Test
	void subjectActionResourceEnvironmentDefaultMapper() {
		var subscription = AuthorizationSubscription.of(SUBJECT, ACTION, RESOURCE, ENVIRONMENT);
		assertAll(() -> assertThat(subscription.getSubject(), is(jsonText(SUBJECT))),
				() -> assertThat(subscription.getAction(), is(jsonText(ACTION))),
				() -> assertThat(subscription.getResource(), is(jsonText(RESOURCE))),
				() -> assertThat(subscription.getEnvironment(), is(jsonText(ENVIRONMENT))));
	}

}
