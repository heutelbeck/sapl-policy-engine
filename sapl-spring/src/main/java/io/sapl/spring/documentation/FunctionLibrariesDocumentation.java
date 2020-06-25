package io.sapl.spring.documentation;

import java.util.Collection;

import io.sapl.interpreter.functions.LibraryDocumentation;
import lombok.Value;

@Value
public class FunctionLibrariesDocumentation {
	Collection<LibraryDocumentation> documentation;
}
