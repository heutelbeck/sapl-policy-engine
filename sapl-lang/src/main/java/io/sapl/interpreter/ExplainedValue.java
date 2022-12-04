package io.sapl.interpreter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.trace.CapturedAttribute;
import lombok.Value;

@Value
public class ExplainedValue {
	private Val                    value;
	private Set<CapturedAttribute> attributes = new HashSet<>();

	public static ExplainedValue of(Val value) {
		return new ExplainedValue(value);
	}

	public static ExplainedValue of(Val value, ExplainedValue... origins) {
		return new ExplainedValue(value, origins);
	}

	public static ExplainedValue of(Val value, List<ExplainedValue> origins) {
		return new ExplainedValue(value, origins);
	}

	public static ExplainedValue of(Val value, CapturedAttribute attribute) {
		return new ExplainedValue(value, attribute);
	}

	private ExplainedValue(Val value) {
		this.value = value;
	}

	private ExplainedValue(Val value, CapturedAttribute attribute) {
		this.value = value;
		attributes.add(attribute);
	}

	private ExplainedValue(Val value, Iterable<ExplainedValue> origins) {
		this.value = value;
		for (var origin : origins) {
			attributes.addAll(origin.getAttributes());
		}
	}

	private ExplainedValue(Val value, ExplainedValue... origins) {
		this.value = value;
		for (var origin : origins) {
			attributes.addAll(origin.getAttributes());
		}
	}
}
