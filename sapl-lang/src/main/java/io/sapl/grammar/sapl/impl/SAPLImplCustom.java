/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.HashMap;
import java.util.Map;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.LibraryFunctionProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Slf4j
public class SAPLImplCustom extends SAPLImpl {

	private static final String IMPORT_EXISTS = "An import for name '%s' already exists.";

	private static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";

	private static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";

	private static final String LIBRARY_IMPORT_EXISTS = "Library import of '%s' not possible as an import for name '%s' already exists.";

	@Override
	public Mono<Val> matches() {
		return getPolicyElement().matches().contextWrite(this::loadImportsIntoContext)
				.onErrorResume(error -> Mono.just(Val.error(error)));
	}

	@Override
	public Flux<AuthorizationDecision> evaluate() {
		return getPolicyElement().evaluate().contextWrite(this::loadImportsIntoContext)
				.doOnError(error -> log.debug("  |- INDETERMINATE. The imports evaluated with en error: {}",
						error.getMessage()))
				.onErrorReturn(AuthorizationDecision.INDETERMINATE);
	}

	private Context loadImportsIntoContext(Context ctx) {
		var imports = fetchImports(this, AuthorizationContext.getAttributeContext(ctx),
				AuthorizationContext.functionContext(ctx));
		return AuthorizationContext.setImports(ctx, imports);
	}

	public static Map<String, String>
			fetchImports(SAPL sapl, AttributeContext attributeContext, FunctionContext functionContext) {
		var imports = new HashMap<String, String>();
		for (var anImport : sapl.getImports()) {
			addImport(anImport, imports, attributeContext, functionContext);
		}
		return imports;
	}

	private static void addImport(
			Import anImport,
			Map<String, String> imports,
			AttributeContext attributeContext,
			FunctionContext functionContext) {
		var library = String.join(".", anImport.getLibSteps());

		if (anImport instanceof WildcardImport) {
			addWildcardImports(imports, library, attributeContext);
			addWildcardImports(imports, library, functionContext);
		} else if (anImport instanceof LibraryImport) {
			var alias = ((LibraryImport) anImport).getLibAlias();
			addLibraryImports(imports, library, alias, attributeContext);
			addLibraryImports(imports, library, alias, functionContext);
		} else {
			addBasicImport(anImport, library, imports, attributeContext, functionContext);
		}
	}

	private static void addBasicImport(
			Import anImport,
			String library,
			Map<String, String> imports,
			AttributeContext attributeContext,
			FunctionContext functionContext) {
		var functionName               = anImport.getFunctionName();
		var fullyQualifiedFunctionName = String.join(".", library, functionName);

		if (imports.containsKey(functionName))
			throw new PolicyEvaluationException(IMPORT_EXISTS, fullyQualifiedFunctionName);

		if (evaluationContextProvidesFunction(attributeContext, functionContext, fullyQualifiedFunctionName))
			imports.put(functionName, fullyQualifiedFunctionName);
		else
			throw new PolicyEvaluationException(IMPORT_NOT_FOUND, fullyQualifiedFunctionName);
	}

	private static boolean evaluationContextProvidesFunction(
			AttributeContext attributeContext,
			FunctionContext functionContext,
			String fullyQualifiedFunctionName) {
		return functionContext.isProvidedFunction(fullyQualifiedFunctionName)
				|| attributeContext.isProvidedFunction(fullyQualifiedFunctionName);
	}

	private static void addWildcardImports(
			Map<String, String> imports,
			String library,
			LibraryFunctionProvider functionProvider) {
		for (var name : functionProvider.providedFunctionsOfLibrary(library)) {
			log.info("name=" + name);
			if (imports.put(name, String.join(".", library, name)) != null)
				throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS, library, name);
			log.info("check");
		}
	}

	private static void addLibraryImports(
			Map<String, String> imports,
			String library,
			String alias,
			LibraryFunctionProvider functionProvider) {
		for (var name : functionProvider.providedFunctionsOfLibrary(library)) {
			var key = String.join(".", alias, name);
			if (imports.put(key, String.join(".", library, name)) != null)
				throw new PolicyEvaluationException(LIBRARY_IMPORT_EXISTS, library, name);
		}
	}

	@Override
	public String toString() {
		return getPolicyElement().getSaplName();
	}

}
