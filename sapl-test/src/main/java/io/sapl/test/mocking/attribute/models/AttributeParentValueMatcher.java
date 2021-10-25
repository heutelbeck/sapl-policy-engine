package io.sapl.test.mocking.attribute.models;

import io.sapl.api.interpreter.Val;

import org.hamcrest.Matcher;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AttributeParentValueMatcher {
	private Matcher<Val> matcher;
}
