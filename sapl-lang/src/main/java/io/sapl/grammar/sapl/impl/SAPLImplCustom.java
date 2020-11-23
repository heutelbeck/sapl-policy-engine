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
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.pip.LibraryFunctionProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class SAPLImplCustom extends SAPLImpl {

	private static final String IMPORT_EXISTS = "An import for name '%s' already exists.";
	private static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";
	private static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";
	private static final String LIBRARY_IMPORT_EXISTS = "Library import of '%s' not possible as an import for name '%s' already exists.";

	@Override
	public Mono<Val> matches(EvaluationContext subscriptionScopedEvaluationContext) {
		try {
			return getPolicyElement().matches(documentScopedEvaluationContext(subscriptionScopedEvaluationContext));
		} catch (PolicyEvaluationException e) {
			log.trace("| | |-- Error during matching: {}", e.getMessage());
			return Mono.just(Val.error(e));
		}
	}

	@Override
	public Flux<AuthorizationDecision> evaluate(EvaluationContext subscriptionScopedEvaluationContext) {
		log.trace("| | |-- SAPL Evaluate: {} ({})", getPolicyElement().getSaplName(),
				getPolicyElement().getClass().getName());
		EvaluationContext documentScopedEvaluationContext;
		try {
			documentScopedEvaluationContext = documentScopedEvaluationContext(subscriptionScopedEvaluationContext);
		} catch (PolicyEvaluationException e) {
			log.trace("| | |-- INDETERMINATE. The imports evaluated with en error: {}", e.getMessage());
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		return getPolicyElement().evaluate(documentScopedEvaluationContext).doOnNext(this::logAuthzDecision);
	}

	@Override
	public EvaluationContext documentScopedEvaluationContext(EvaluationContext subscriptionScopedEvaluationContext) {
		return subscriptionScopedEvaluationContext.withImports(fetchImports(subscriptionScopedEvaluationContext));
	}

	private Map<String, String> fetchImports(EvaluationContext subscriptionScopedEvaluationContext) {
		var imports = new HashMap<String, String>();
		for (var anImport : getImports()) {
			addImport(anImport, imports, subscriptionScopedEvaluationContext);
		}
		return imports;
	}

	private void addImport(Import anImport, Map<String, String> imports,
			EvaluationContext subscriptionScopedEvaluationContext) {
		var library = String.join(".", anImport.getLibSteps());
		if (anImport instanceof WildcardImport) {
			addWildcardImports(imports, library, subscriptionScopedEvaluationContext.getAttributeCtx());
			addWildcardImports(imports, library, subscriptionScopedEvaluationContext.getFunctionCtx());
		} else if (anImport instanceof LibraryImport) {
			var alias = ((LibraryImport) anImport).getLibAlias();
			addLibraryImports(imports, library, alias, subscriptionScopedEvaluationContext.getAttributeCtx());
			addLibraryImports(imports, library, alias, subscriptionScopedEvaluationContext.getFunctionCtx());
		} else
			addBasicImport(anImport, library, imports, subscriptionScopedEvaluationContext);
	}

	private void addBasicImport(Import anImport, String library, Map<String, String> imports,
			EvaluationContext subscriptionScopedEvaluationContext) {
		var functionName = anImport.getFunctionName();
		var fullyQualifiedFunctionName = String.join(".", library, functionName);

		if (imports.containsKey(functionName))
			throw new PolicyEvaluationException(IMPORT_EXISTS, fullyQualifiedFunctionName);

		if (evaluationContextProvidesFunction(subscriptionScopedEvaluationContext, fullyQualifiedFunctionName))
			imports.put(functionName, fullyQualifiedFunctionName);
		else
			throw new PolicyEvaluationException(IMPORT_NOT_FOUND, fullyQualifiedFunctionName);
	}

	private boolean evaluationContextProvidesFunction(EvaluationContext subscriptionScopedEvaluationContext,
			String fullyQualifiedFunctionName) {
		return subscriptionScopedEvaluationContext.getFunctionCtx().isProvidedFunction(fullyQualifiedFunctionName)
				|| subscriptionScopedEvaluationContext.getAttributeCtx().isProvidedFunction(fullyQualifiedFunctionName);
	}

	private void addWildcardImports(Map<String, String> imports, String library,
			LibraryFunctionProvider functionProvider) {
		for (var name : functionProvider.providedFunctionsOfLibrary(library)) {
			if (imports.put(name, String.join(".", library, name)) != null)
				throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS, library, name);
		}
	}

	private void addLibraryImports(Map<String, String> imports, String library, String alias,
			LibraryFunctionProvider functionProvider) {
		for (var name : functionProvider.providedFunctionsOfLibrary(library)) {
			var key = String.join(".", alias, name);
			if (imports.put(key, String.join(".", library, name)) != null)
				throw new PolicyEvaluationException(LIBRARY_IMPORT_EXISTS, library, name);
		}
	}

	private void logAuthzDecision(AuthorizationDecision r) {
		log.trace("| | |-- {} document {} evaluated to: {}", r.getDecision(), getPolicyElement().getSaplName(), r);
	}

}
