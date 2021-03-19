package io.sapl.api.pdp;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IdentifiableAuthorizationDecisionTest {

	@Test
	void subjectActionResourceDefaultMapper() {
		var subscription = IdentifiableAuthorizationDecision.INDETERMINATE;

		assertAll(() -> assertThat(subscription.getAuthorizationSubscriptionId(), is(nullValue())),
				() -> assertEquals(Decision.INDETERMINATE, subscription.getAuthorizationDecision().getDecision()));
	}

}
