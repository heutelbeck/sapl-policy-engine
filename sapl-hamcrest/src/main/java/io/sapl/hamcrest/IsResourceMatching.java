package io.sapl.hamcrest;

import java.util.Objects;
import java.util.function.Predicate;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class IsResourceMatching extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final Predicate<? super JsonNode> predicate;

	public IsResourceMatching(Predicate<? super JsonNode> jsonPredicate) {
		super(AuthorizationDecision.class);
		this.predicate = Objects.requireNonNull(jsonPredicate);
	}


	@Override
	public void describeTo(Description description) {
		description.appendText("the decision has a resource matching the predicate");
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getResource().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain a resource");
			return false;
		}
		
		var json = decision.getResource().get();
		if (this.predicate.test(json)) {
			return true;
		} else {
			mismatchDescription.appendText("was resource that matches the predicate");
			return false;
		}
	}
}
