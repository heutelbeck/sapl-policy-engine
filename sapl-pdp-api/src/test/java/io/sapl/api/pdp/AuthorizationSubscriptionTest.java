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
