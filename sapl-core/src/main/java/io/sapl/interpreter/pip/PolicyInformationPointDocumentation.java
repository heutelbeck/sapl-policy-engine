package io.sapl.interpreter.pip;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;

@Data
public class PolicyInformationPointDocumentation {
	@NonNull
	String name;
	@NonNull
	String description;
	@NonNull
	Object policyInformationPoint;
	Map<String, String> documentation = new HashMap<>();
}
