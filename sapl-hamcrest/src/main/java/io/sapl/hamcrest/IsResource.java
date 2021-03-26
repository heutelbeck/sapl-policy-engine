package io.sapl.hamcrest;

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class IsResource extends TypeSafeDiagnosingMatcher<AuthorizationDecision> {

	private final Optional<Matcher<? super JsonNode>> jsonMatcher;

	public IsResource(Matcher<? super JsonNode> jsonMatcher) {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
	}

	public IsResource() {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.empty();
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("a resource with");
		jsonMatcher.ifPresentOrElse(matcher -> description.appendDescriptionOf(matcher),
				() -> description.appendText(" any JsonNode"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getResource().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain a resource");
			return false;
		}
		
		var json = decision.getResource().get();
		if (jsonMatcher.isEmpty() || jsonMatcher.get().matches(json)) {
			return true;
		} else {
			mismatchDescription.appendText("was resource that ");
			jsonMatcher.get().describeMismatch(json, mismatchDescription);
			return false;
		}
	}

}