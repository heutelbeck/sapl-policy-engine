package io.sapl.hamcrest;

import java.util.Objects;
import java.util.function.Predicate;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasObligationMatching extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final Predicate<? super JsonNode> predicate;

	public HasObligationMatching(Predicate<? super JsonNode> jsonPredicate) {
		super(AuthorizationDecision.class);
		this.predicate = Objects.requireNonNull(jsonPredicate);
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has an obligation matching the predicate");
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getObligations().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain any obligations");
			return false;
		}
		
		boolean containsObligation = false;
		
        for(JsonNode node : decision.getObligations().get()) {
        	if(this.predicate.test(node))
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
