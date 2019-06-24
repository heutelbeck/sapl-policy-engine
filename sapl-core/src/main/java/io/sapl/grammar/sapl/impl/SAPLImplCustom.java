package io.sapl.grammar.sapl.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Request;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;

public class SAPLImplCustom extends SAPLImpl {

    private static final String IMPORT_EXISTS = "An import for name '%s' already exists.";
    private static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";

    @Override
    public boolean matches(Request request, EvaluationContext ctx) throws PolicyEvaluationException {
        final Map<String, String> functionImports = fetchFunctionImports(ctx.getFunctionCtx());
        final EvaluationContext evaluationCtx = new EvaluationContext(ctx.getFunctionCtx(), ctx.getVariableCtx(), functionImports);
        return getPolicyElement().matches(request, evaluationCtx);
    }

    @Override
    public Map<String, String> fetchFunctionImports(FunctionContext functionCtx)
            throws PolicyEvaluationException {
        final Map<String, String> imports = new HashMap<>();

        for (Import anImport : getImports()) {
            final String library = String.join(".", anImport.getLibSteps());

            if (anImport instanceof WildcardImport) {
                imports.putAll(fetchWildcardImports(imports, library,
                        functionCtx.functionsInLibrary(library)));
            }
            else if (anImport instanceof LibraryImport) {
                final String alias = ((LibraryImport) anImport).getLibAlias();
                imports.putAll(fetchLibraryImports(imports, library, alias,
                        functionCtx.functionsInLibrary(library)));
            }
            else {
                final String functionName = anImport.getFunctionName();
                final String fullyQualified = String.join(".", library, functionName);

                if (imports.containsKey(anImport.getFunctionName())) {
                    throw new PolicyEvaluationException(
                            String.format(IMPORT_EXISTS, fullyQualified));
                }
                imports.put(functionName, fullyQualified);
            }
        }

        return imports;
    }

    private Map<String, String> fetchWildcardImports(Map<String, String> imports, String library,
                Collection<String> libraryItems) throws PolicyEvaluationException {
        final Map<String, String> returnImports = new HashMap<>(libraryItems.size(),
                1.0F);
        for (String name : libraryItems) {
            if (imports.containsKey(name)) {
                throw new PolicyEvaluationException(
                        String.format(WILDCARD_IMPORT_EXISTS, library, name));
            }
            else {
                returnImports.put(name, String.join(".", library, name));
            }
        }
        return returnImports;
    }

    private Map<String, String> fetchLibraryImports(Map<String, String> imports,
                String library, String alias, Collection<String> libraryItems)
            throws PolicyEvaluationException {
        final Map<String, String> returnImports = new HashMap<>(libraryItems.size(),
                1.0F);
        for (String name : libraryItems) {
            String key = String.join(".", alias, name);
            if (imports.containsKey(key)) {
                throw new PolicyEvaluationException(
                        String.format(WILDCARD_IMPORT_EXISTS, library, name));
            }
            else {
                returnImports.put(key, String.join(".", library, name));
            }
        }
        return returnImports;
    }
}
