package io.sapl.api.pdp;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AuthorizationDecisionTest {

	@Test
	void defaultConstructorResultsInNoEntriesAndIndeterminate() {
		var decision = new AuthorizationDecision();
		assertAll(() -> assertEquals(Decision.INDETERMINATE, decision.getDecision()),
				() -> assertThat(decision.getAdvices(), isEmpty()),
				() -> assertThat(decision.getObligations(), isEmpty()),
				() -> assertThat(decision.getResource(), isEmpty()));
	}
}
