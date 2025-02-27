/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.sapl.impl.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;

@UtilityClass
public class ImportsUtil {
    private static final String IMPORT_EXISTS_ERROR          = "An import for name '%s' already exists.";
    private static final String IMPORT_NOT_FOUND_ERROR       = "Import '%s' was not found.";
    private static final String WILDCARD_IMPORT_EXISTS_ERROR = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";
    private static final String LIBRARY_IMPORT_EXISTS_ERROR  = "Library import of '%s' not possible as an import for name '%s' already exists.";

    public static Context loadImportsIntoContext(EObject startNode, Context ctx) {
        final var imports = fetchImportsFromParents(startNode, AuthorizationContext.getAttributeStreamBroker(ctx),
                AuthorizationContext.functionContext(ctx));
        return AuthorizationContext.setImports(ctx, imports);
    }

    private static Map<String, String> fetchImportsFromParents(EObject startNode,
            AttributeStreamBroker attributeStreamBroker, FunctionContext functionContext) {
        if (startNode == null)
            return Map.of();

        if (startNode instanceof SAPL sapl)
            return fetchImports(sapl, attributeStreamBroker, functionContext);

        return fetchImportsFromParents(startNode.eContainer(), attributeStreamBroker, functionContext);
    }

    public static Map<String, String> fetchImports(SAPL sapl, AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext) {
        final var imports = new HashMap<String, String>();
        for (var anImport : sapl.getImports()) {
            addImport(anImport, imports, attributeStreamBroker, functionContext);
        }
        return imports;
    }

    private static void addImport(Import anImport, Map<String, String> imports, AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext) {
        final var library = String.join(".", anImport.getLibSteps());

        if (anImport instanceof WildcardImport) {
            addWildcardImports(imports, library, attributeStreamBroker.providedFunctionsOfLibrary(library));
            addWildcardImports(imports, library, functionContext.providedFunctionsOfLibrary(library));
        } else if (anImport instanceof LibraryImport libraryImport) {
            final var alias = libraryImport.getLibAlias();
            addLibraryImports(imports, attributeStreamBroker.providedFunctionsOfLibrary(library), library, alias);
            addLibraryImports(imports, functionContext.providedFunctionsOfLibrary(library), library, alias);
        } else {
            addBasicImport(anImport, library, imports, attributeStreamBroker, functionContext);
        }
    }

    private static void addBasicImport(Import anImport, String library, Map<String, String> imports,
            AttributeStreamBroker attributeStreamBroker, FunctionContext functionContext) {
        final var functionName               = anImport.getFunctionName();
        final var fullyQualifiedFunctionName = String.join(".", library, functionName);

        if (imports.containsKey(functionName))
            throw new PolicyEvaluationException(IMPORT_EXISTS_ERROR, fullyQualifiedFunctionName);

        if (evaluationContextProvidesFunction(attributeStreamBroker, functionContext, fullyQualifiedFunctionName))
            imports.put(functionName, fullyQualifiedFunctionName);
        else
            throw new PolicyEvaluationException(IMPORT_NOT_FOUND_ERROR, fullyQualifiedFunctionName);
    }

    private static boolean evaluationContextProvidesFunction(  AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext, String fullyQualifiedFunctionName) {
        return functionContext.isProvidedFunction(fullyQualifiedFunctionName)
                || attributeStreamBroker.isProvidedFunction(fullyQualifiedFunctionName);
    }

    private static void addWildcardImports(Map<String, String> imports, String library,
            Collection<String> libraryFunctions) {
        for (var name : libraryFunctions) {
            if (imports.put(name, String.join(".", library, name)) != null)
                throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS_ERROR, library, name);
        }
    }

    private static void addLibraryImports(Map<String, String> imports, Collection<String> libraryFunctions, String library, String alias) {
        for (var name : libraryFunctions) {
            final var key = String.join(".", alias, name);
            if (imports.put(key, String.join(".", library, name)) != null)
                throw new PolicyEvaluationException(LIBRARY_IMPORT_EXISTS_ERROR, library, name);
        }
    }
   
}
