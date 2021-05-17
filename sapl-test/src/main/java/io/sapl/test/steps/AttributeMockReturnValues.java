package io.sapl.test.steps;

import io.sapl.api.interpreter.Val;
import lombok.Data;

@Data(staticConstructor = "of")
public class AttributeMockReturnValues {
	private final String fullname;
	private final Val[] mockReturnValues;
}