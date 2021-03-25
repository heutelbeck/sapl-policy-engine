package io.sapl.hamcrest;

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

public class IsDecision extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {
	
	private Optional<Decision> expectedDecision;
	
	public IsDecision(Decision expected) {
		super(AuthorizationDecision.class);
		this.expectedDecision = Optional.of(Objects.requireNonNull(expected));
	}
	
	public IsDecision() {
		super(AuthorizationDecision.class);
		this.expectedDecision = Optional.empty();
	}
	
	@Override
	public void describeTo(Description description) {
		description.appendText("the decision is ");
		this.expectedDecision.ifPresentOrElse(expected -> description.appendText(expected.name()),
				() -> description.appendText("any decision"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if (this.expectedDecision.isEmpty() || this.expectedDecision.get() == decision.getDecision()) {
			return true;
		} else {
			mismatchDescription.appendText("was decision of " + this.expectedDecision.get().name());
			return false;
		}
	}

}
