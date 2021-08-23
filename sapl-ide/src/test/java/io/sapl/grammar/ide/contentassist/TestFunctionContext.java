package io.sapl.grammar.ide.contentassist;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;

public class TestFunctionContext implements FunctionContext {

	private final Map<String, Set<String>> availableLibraries;
	
	public TestFunctionContext() {
		availableLibraries = new HashMap<>();
		availableLibraries.put("filter", Set.of("blacken", "remove", "replace"));
		availableLibraries.put("standard", Set.of("length", "numberToString"));
		availableLibraries.put("time", Set.of("after", "before", "between"));
	}
	
	@Override
	public Boolean isProvidedFunction(String function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<String> providedFunctionsOfLibrary(String pipName) {
		return availableLibraries.getOrDefault(pipName, Set.of());
	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return availableLibraries.keySet();
	}

	@Override
	public Val evaluate(String function, Val... parameters) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void loadLibrary(Object library) throws InitializationException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<LibraryDocumentation> getDocumentation() {
		throw new UnsupportedOperationException();
	}

}
