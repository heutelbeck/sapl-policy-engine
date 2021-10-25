package io.sapl.test.mocking.attribute.models;

import io.sapl.api.interpreter.Val;

import org.hamcrest.Matcher;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AttributeArgumentMatchers {
	private Matcher<Val>[] matchers;
}
