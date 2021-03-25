package io.sapl.hamcrest;

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasAdvice extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final Optional<Matcher<? super JsonNode>> jsonMatcher;

	public HasAdvice(Matcher<? super JsonNode> jsonMatcher) {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.of(Objects.requireNonNull(jsonMatcher));
	}

	public HasAdvice() {
		super(AuthorizationDecision.class);
		this.jsonMatcher = Optional.empty();
	}
	
	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has an advice equals ");
		this.jsonMatcher.ifPresentOrElse(matcher -> description.appendDescriptionOf(matcher),
				() -> description.appendText("any advice"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getAdvices().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain any advices");
			return false;
		}
		
		if (jsonMatcher.isEmpty()) {
			return true;
		} 
		
		boolean containsAdvice = false;
		
        for(JsonNode node : decision.getAdvices().get()) {
        	if(this.jsonMatcher.get().matches(node))
        		containsAdvice = true;
        };
        
		if(containsAdvice) {
			return true;
		} else {
			mismatchDescription.appendText("no advice matched");
			return false;
		}
	}

}
