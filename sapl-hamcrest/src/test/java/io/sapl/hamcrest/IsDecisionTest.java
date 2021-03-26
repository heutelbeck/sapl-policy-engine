package io.sapl.hamcrest;

import static io.sapl.hamcrest.Matchers.anyAuthDecision;
import static io.sapl.hamcrest.Matchers.isDeny;
import static io.sapl.hamcrest.Matchers.isIndeterminate;
import static io.sapl.hamcrest.Matchers.isNotApplicable;
import static io.sapl.hamcrest.Matchers.isPermit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class IsDecisionTest {

	@Test
	void testPermit() {
		var sut = isPermit();
		assertThat(new AuthorizationDecision(Decision.PERMIT), is(sut));
		assertThat(new AuthorizationDecision(Decision.DENY), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.NOT_APPLICABLE), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.INDETERMINATE), not(is(sut)));
	}
	
	@Test
	void testDeny() {
		var sut = isDeny();
		assertThat(new AuthorizationDecision(Decision.PERMIT), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.DENY), is(sut));
		assertThat(new AuthorizationDecision(Decision.NOT_APPLICABLE), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.INDETERMINATE), not(is(sut)));
	}
	
	@Test
	void testIsNotApplicable() {
		var sut = isNotApplicable();
		assertThat(new AuthorizationDecision(Decision.PERMIT), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.DENY), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.NOT_APPLICABLE), is(sut));
		assertThat(new AuthorizationDecision(Decision.INDETERMINATE), not(is(sut)));
	}
	
	@Test
	void testIndeterminate() {
		var sut = isIndeterminate();
		assertThat(new AuthorizationDecision(Decision.PERMIT), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.DENY), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.NOT_APPLICABLE), not(is(sut)));
		assertThat(new AuthorizationDecision(Decision.INDETERMINATE), is(sut));
	}
	
	@Test
	void testAnyDec() {
		var sut = anyAuthDecision();
		assertThat(new AuthorizationDecision(Decision.PERMIT), is(sut));
		assertThat(new AuthorizationDecision(Decision.DENY), is(sut));
		assertThat(new AuthorizationDecision(Decision.NOT_APPLICABLE), is(sut));
		assertThat(new AuthorizationDecision(Decision.INDETERMINATE), is(sut));
	}

	@Test
	void testDescriptionForEmptyConstructor() {
		var sut = new IsDecision();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("the decision is any decision"));
	}
	
	@Test
	void testDescriptionForDecisionConstructor() {
		var sut = isPermit();
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("the decision is PERMIT"));
	}
}
