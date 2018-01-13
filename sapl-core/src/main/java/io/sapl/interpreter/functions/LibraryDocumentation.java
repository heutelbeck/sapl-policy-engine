package io.sapl.interpreter.functions;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;

@Data
public class LibraryDocumentation {
	@NonNull
	String name;
	@NonNull
	String description;
	@NonNull
	Object library;
	Map<String, String> documentation = new HashMap<>();
}
