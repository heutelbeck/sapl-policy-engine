package io.sapl.grammar.sapl.impl.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.LibraryFunctionProvider;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;

@UtilityClass
public class ImportsUtil {
	private static final String IMPORT_EXISTS = "An import for name '%s' already exists.";

	private static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";

	private static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";

	private static final String LIBRARY_IMPORT_EXISTS = "Library import of '%s' not possible as an import for name '%s' already exists.";

	public static Context loadImportsIntoContext(EObject startNode, Context ctx) {
		var imports = fetchImportsFromParents(startNode, AuthorizationContext.getAttributeContext(ctx),
				AuthorizationContext.functionContext(ctx));
		return AuthorizationContext.setImports(ctx, imports);
	}

	private static Map<String, String> fetchImportsFromParents(EObject startNode, AttributeContext attributeContext,
			FunctionContext functionContext) {
		if (startNode == null)
			return Map.of();

		if (startNode instanceof SAPL sapl)
			return fetchImports(sapl, attributeContext, functionContext);

		return fetchImportsFromParents(startNode.eContainer(), attributeContext, functionContext);
	}

	public static Map<String, String> fetchImports(SAPL sapl, AttributeContext attributeContext,
			FunctionContext functionContext) {
		var imports = new HashMap<String, String>();
		for (var anImport : sapl.getImports()) {
			addImport(anImport, imports, attributeContext, functionContext);
		}
		return imports;
	}

	private static void addImport(Import anImport, Map<String, String> imports, AttributeContext attributeContext,
			FunctionContext functionContext) {
		var library = String.join(".", anImport.getLibSteps());

		if (anImport instanceof WildcardImport) {
			addWildcardImports(imports, library, attributeContext);
			addWildcardImports(imports, library, functionContext);
		} else if (anImport instanceof LibraryImport libraryImport) {
			var alias = libraryImport.getLibAlias();
			addLibraryImports(imports, library, alias, attributeContext);
			addLibraryImports(imports, library, alias, functionContext);
		} else {
			addBasicImport(anImport, library, imports, attributeContext, functionContext);
		}
	}

	private static void addBasicImport(Import anImport, String library, Map<String, String> imports,
			AttributeContext attributeContext, FunctionContext functionContext) {
		var functionName               = anImport.getFunctionName();
		var fullyQualifiedFunctionName = String.join(".", library, functionName);

		if (imports.containsKey(functionName))
			throw new PolicyEvaluationException(IMPORT_EXISTS, fullyQualifiedFunctionName);

		if (evaluationContextProvidesFunction(attributeContext, functionContext, fullyQualifiedFunctionName))
			imports.put(functionName, fullyQualifiedFunctionName);
		else
			throw new PolicyEvaluationException(IMPORT_NOT_FOUND, fullyQualifiedFunctionName);
	}

	private static boolean evaluationContextProvidesFunction(AttributeContext attributeContext,
			FunctionContext functionContext, String fullyQualifiedFunctionName) {
		return functionContext.isProvidedFunction(fullyQualifiedFunctionName)
				|| attributeContext.isProvidedFunction(fullyQualifiedFunctionName);
	}

	private static void addWildcardImports(Map<String, String> imports, String library,
			LibraryFunctionProvider functionProvider) {
		for (var name : functionProvider.providedFunctionsOfLibrary(library)) {
			if (imports.put(name, String.join(".", library, name)) != null)
				throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS, library, name);
		}
	}

	private static void addLibraryImports(Map<String, String> imports, String library, String alias,
			LibraryFunctionProvider functionProvider) {
		for (var name : functionProvider.providedFunctionsOfLibrary(library)) {
			var key = String.join(".", alias, name);
			if (imports.put(key, String.join(".", library, name)) != null)
				throw new PolicyEvaluationException(LIBRARY_IMPORT_EXISTS, library, name);
		}
	}

}
