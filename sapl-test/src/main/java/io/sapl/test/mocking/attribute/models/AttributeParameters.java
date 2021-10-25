package io.sapl.test.mocking.attribute.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AttributeParameters {
	private AttributeParentValueMatcher parentValueMatcher;
	private AttributeArgumentMatchers argumentMatchers;
}
