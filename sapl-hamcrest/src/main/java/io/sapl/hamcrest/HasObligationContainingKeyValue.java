package io.sapl.hamcrest;

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasObligationContainingKeyValue extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final String key;
	private final Optional<Matcher<? super JsonNode>> valueMatcher;


	public HasObligationContainingKeyValue(String key, Matcher<? super JsonNode> value) {
		super(AuthorizationDecision.class);
		this.key = Objects.requireNonNull(key);
		this.valueMatcher = Optional.of(Objects.requireNonNull(value));
	}
	
	public HasObligationContainingKeyValue(String key) {
		super(AuthorizationDecision.class);
		this.key = Objects.requireNonNull(key);
		this.valueMatcher = Optional.empty();
	}

	@Override
	public void describeTo(Description description) {
		description.appendText(String.format("the decision has an obligation containing key %s", this.key));

		this.valueMatcher.ifPresentOrElse(matcher -> description.appendText(" with ").appendDescriptionOf(matcher),
				() -> description.appendText(" with any value"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getObligations().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain any obligations");
			return false;
		}
		
		
		boolean containsObligationKeyValue = false;
		
		//iterate over all obligations
        for(JsonNode obligation : decision.getObligations().get()) {
        	var iterator = obligation.fields();
        	//iterate over fields in this obligation
        	while (iterator.hasNext()) {
        	    var entry = iterator.next();
        	    //check if key/value exists
        	    if(entry.getKey().equals(this.key))  {
        	    	if(this.valueMatcher.isEmpty()) {
        	    		containsObligationKeyValue = true;        	    		
        	    	} else if(this.valueMatcher.get().matches(entry.getValue())) {
        	    		containsObligationKeyValue = true;
        	    	}
        		}
        	}
        };
        
		if(containsObligationKeyValue) {
			return true;
		} else {
			mismatchDescription.appendText("no entry in all obligations matched");
			return false;
		}
	}

}
