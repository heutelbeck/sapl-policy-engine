package io.sapl.grammar.ide.contentassist.schema;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;

import java.util.*;

public class TestFunctionCtx implements FunctionContext {

    private final Map<String, Set<String>> availableLibraries;

    final String PERSON_SCHEMA = """
					{
					  "type": "object",
					  "properties": {
						"name": { "type": "string" }
					  }
					}
					""";

    final String DOG_SCHEMA = """
					{
					  "type": "object",
					  "properties": {
						"race": { "type": "string" }
					  }
					}
					""";

    public TestFunctionCtx() {
        availableLibraries = new HashMap<>();
        availableLibraries.put("schemaTest", Set.of("person", "dog"));
    }


    @Override
    public Val evaluate(String function, Val... parameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<LibraryDocumentation> getDocumentation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getCodeTemplates() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getDocumentedCodeTemplates() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> getFunctionSchemas() {
        var schemas = new HashMap<String, String>();
        schemas.put("person", PERSON_SCHEMA);
        schemas.put("dog", DOG_SCHEMA);
        return schemas;
    }

    @Override
    public List<String> getAllFunctionSchemas() {
        var schemas = new LinkedList<String>();
        schemas.add(String.join(".","person", PERSON_SCHEMA));
        return schemas;
    }

    @Override
    public Boolean isProvidedFunction(String function) {
        if (function.equals("schemaTest"))
            return true;
        return false;
    }

    @Override
    public Collection<String> providedFunctionsOfLibrary(String pipName) {
        return List.of("person", "dog");
    }

    @Override
    public Collection<String> getAvailableLibraries() {
        return List.of("schemaTest");
    }

    @Override
    public Collection<String> getAllFullyQualifiedFunctions() {
        return List.of("schemaTest.person", "schemaTest.dog");
    }
}
