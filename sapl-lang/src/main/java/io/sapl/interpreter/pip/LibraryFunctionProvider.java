package io.sapl.interpreter.pip;

import java.util.Collection;

public interface LibraryFunctionProvider {

	Boolean isProvidedFunction(String function);

	Collection<String> providedFunctionsOfLibrary(String pipName);

}