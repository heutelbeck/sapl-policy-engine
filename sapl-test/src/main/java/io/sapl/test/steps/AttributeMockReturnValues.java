package io.sapl.test.steps;

import java.util.List;

import io.sapl.api.interpreter.Val;
import lombok.Data;

@Data(staticConstructor = "of")
public class AttributeMockReturnValues {
	private final String fullname;
	private final List<Val> mockReturnValues;
}