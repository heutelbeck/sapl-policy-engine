package io.sapl.api.interpreter;

import lombok.Value;

@Value
public class ExpressionArgument {
	String name;
	Val    value;
}