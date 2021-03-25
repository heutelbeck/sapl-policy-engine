package io.sapl.hamcrest;

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasObligation extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final Optional<Matcher<? super JsonNode>> jsonMatcher;

	public HasObligation(Matcher<? super JsonNode> jsonMatcher) {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
	}

	public HasObligation() {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.empty();
	}
	
	
	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has an obligation equals ");
		this.jsonMatcher.ifPresentOrElse(matcher -> description.appendDescriptionOf(matcher),
				() -> description.appendText("any obligation"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getObligations().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain any obligations");
			return false;
		}
		
		if (jsonMatcher.isEmpty()) {
			return true;
		} 
		
		boolean containsObligation = false;
		
        for(JsonNode node : decision.getObligations().get()) {
        	if(this.jsonMatcher.get().matches(node))
        		containsObligation = true;
        };
        
		if(containsObligation) {
			return true;
		} else {
			mismatchDescription.appendText("no obligation matched");
			return false;
		}
	}
}