/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.hamcrest;

import java.util.Objects;
import java.util.Optional;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;

public class HasAdviceContainingKeyValue extends TypeSafeDiagnosingMatcher<AuthorizationDecision>  {
	
	private final String key;
	private final Optional<Matcher<? super JsonNode>> valueMatcher;


	public HasAdviceContainingKeyValue(String key, Matcher<? super JsonNode> value) {
		super(AuthorizationDecision.class);
		this.key = Objects.requireNonNull(key);
		this.valueMatcher = Optional.of(Objects.requireNonNull(value));
	}
	
	public HasAdviceContainingKeyValue(String key) {
		super(AuthorizationDecision.class);
		this.key = Objects.requireNonNull(key);
		this.valueMatcher = Optional.empty();
	}
	
	@Override
	public void describeTo(Description description) {
		description.appendText(String.format("the decision has an advice containing key %s", this.key));

		this.valueMatcher.ifPresentOrElse(matcher -> description.appendText(" with ").appendDescriptionOf(matcher),
				() -> description.appendText(" with any value"));
	}

	@Override
	protected boolean matchesSafely(AuthorizationDecision decision, Description mismatchDescription) {
		if(decision.getAdvice().isEmpty())
		{
			mismatchDescription.appendText("decision didn't contain any advice");
			return false;
		}
		
		
		boolean containsAdvice = false;
		
		//iterate over all advice
        for(JsonNode advice : decision.getAdvice().get()) {
        	var iterator = advice.fields();
        	//iterate over fields in this advice
        	while (iterator.hasNext()) {
        	    var entry = iterator.next();
        	    //check if key/value exists
        	    if(entry.getKey().equals(this.key))  {
        	    	if(this.valueMatcher.isEmpty()) {
        	    		containsAdvice = true;        	    		
        	    	} else if(this.valueMatcher.get().matches(entry.getValue())) {
            	    	containsAdvice = true;
        	    	}
        		}
        	}
        };
        
		if(containsAdvice) {
			return true;
		} else {
			mismatchDescription.appendText("no entry in advice matched");
			return false;
		}
	}

}
