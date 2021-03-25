package io.sapl.hamcrest;

import java.util.Objects;
import java.util.function.Predicate;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasAdviceMatching extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final Predicate<? super JsonNode> predicate;

	public HasAdviceMatching(Predicate<? super JsonNode> jsonPredicate) {
		super(AuthorizationDecision.class);
		this.predicate = Objects.requireNonNull(jsonPredicate);
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has an advice matching the predicate");
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getAdvices().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain any advices");
			return false;
		}

		boolean containsAdvice = false;
		
        for(JsonNode node : decision.getAdvices().get()) {
        	if(this.predicate.test(node))
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
