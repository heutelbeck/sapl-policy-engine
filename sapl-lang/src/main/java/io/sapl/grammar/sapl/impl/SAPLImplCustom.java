/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class SAPLImplCustom extends SAPLImpl {

	private static final String IMPORT_EXISTS = "An import for name '%s' already exists.";
	private static final String IMPORT_NOT_FOUND = "Import '%s' was not found.";
	private static final String WILDCARD_IMPORT_EXISTS = "Wildcard import of '%s' not possible as an import for name '%s' already exists.";
	private static final String LIBRARY_IMPORT_EXISTS = "Library import of '%s' not possible as an import for name '%s' already exists.";

	/**
	 * Checks whether the SAPL document matches a AuthorizationSubscription by
	 * evaluating the document's target expression. No custom variables are provided
	 * and imports are extracted from the document.
	 * 
	 * @param ctx the evaluation context in which the document's target expression
	 *            is be evaluated. It must contain
	 *            <ul>
	 *            <li>the function context, as functions can be used in the target
	 *            expression</li>
	 *            <li>the variable context holding the four authorization
	 *            subscription variables 'subject', 'action', 'resource' and
	 *            'environment' combined with system variables from the PDP
	 *            configuration</li>
	 *            </ul>
	 * @return {@code true} if the target expression evaluates to {@code true},
	 *         {@code false} otherwise. @ in case there is an error while evaluating
	 *         the target expression
	 */
	@Override
	public Mono<Val> matches(EvaluationContext ctx) {
		try {
			return scopedMatches(createScopedContextWithFunctionImports(ctx));
		} catch (PolicyEvaluationException e) {
			log.trace("| | |-- Error during matching: {}", e.getMessage());
			return Mono.just(Val.error(e));
		}
	}

	private Mono<Val> scopedMatches(EvaluationContext ctx) {
		return getPolicyElement().matches(ctx);
	}

	/**
	 * Evaluates the body of the SAPL document (containing a policy set or a policy)
	 * within the given evaluation context and returns a {@link Flux} of
	 * {@link AuthorizationDecision} objects.
	 * 
	 * @param ctx the evaluation context in which the document's body is evaluated.
	 *            It must contain
	 *            <ul>
	 *            <li>the attribute context</li>
	 *            <li>the function context</li>
	 *            <li>the variable context holding the four authorization
	 *            subscription variables 'subject', 'action', 'resource' and
	 *            'environment' combined with system variables from the PDP
	 *            configuration</li>
	 *            </ul>
	 * @return a {@link Flux} of {@link AuthorizationDecision} objects.
	 */
	@Override
	public Flux<AuthorizationDecision> evaluate(EvaluationContext ctx) {
		EvaluationContext scopedCtx;
		try {
			scopedCtx = createScopedContextWithFunctionAndAttributeImports(ctx);
		} catch (PolicyEvaluationException e) {
			log.trace("| | |-- INDETERMINATE. The imports evaluated with en error: {}", e.getMessage());
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		log.trace("| | |-- SAPL Evaluate: {} ({})", getPolicyElement().getSaplName(),
				getPolicyElement().getClass().getName());
		return Flux.from(scopedMatches(scopedCtx)).switchMap(matches -> {
			if (matches.isError()) {
				log.trace("| | |-- INDETERMINATE. Error in target expression: {}", matches.getMessage());
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}
			if (!matches.getBoolean()) {
				log.trace("| | |-- NOT_APPLICABLE. Target not matching.");
				return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
			}
			log.trace("| | |-- Is applicable: {} ", getPolicyElement().getSaplName());
			return getPolicyElement().evaluate(scopedCtx).doOnNext(this::logAuthzDecision);
		});
	}

	@Override
	public Map<String, String> fetchFunctionImports(FunctionContext functionCtx) {
		return fetchFunctionAndPipImports(functionCtx, null, false);
	}

	private EvaluationContext createScopedContextWithFunctionImports(EvaluationContext originalCtx)
			throws PolicyEvaluationException {
		return originalCtx.withImports(fetchFunctionImports(originalCtx.getFunctionCtx()));
	}

	private EvaluationContext createScopedContextWithFunctionAndAttributeImports(EvaluationContext originalCtx)
			throws PolicyEvaluationException {
		return originalCtx.withImports(
				fetchFunctionAndPipImports(originalCtx.getFunctionCtx(), originalCtx.getAttributeCtx(), true));
	}

	// TODO: get rid of boolean, split functions. this is rude
	private Map<String, String> fetchFunctionAndPipImports(FunctionContext functionCtx, AttributeContext attributeCtx,
			boolean includeAttributes) {
		var imports = new HashMap<String, String>(getImports().size(), 1.0F);
		for (var anImport : getImports()) {
			var library = String.join(".", anImport.getLibSteps());
			if (anImport instanceof WildcardImport) {
				fetchWildcardImports(imports, library, functionCtx.functionsInLibrary(library));
				if (includeAttributes)
					fetchWildcardImports(imports, library, attributeCtx.findersInLibrary(library));
			} else if (anImport instanceof LibraryImport) {
				var alias = ((LibraryImport) anImport).getLibAlias();
				fetchLibraryImports(imports, library, alias, functionCtx.functionsInLibrary(library));
				if (includeAttributes)
					fetchLibraryImports(imports, library, alias, attributeCtx.findersInLibrary(library));
			} else {
				var functionName = anImport.getFunctionName();
				var fullyQualified = String.join(".", library, functionName);
				if (imports.containsKey(functionName)) {
					throw new PolicyEvaluationException(IMPORT_EXISTS, fullyQualified);
				}
				if (!(functionCtx.provides(fullyQualified)
						|| (includeAttributes && attributeCtx.provides(fullyQualified)))) {
					throw new PolicyEvaluationException(IMPORT_NOT_FOUND, fullyQualified);
				}
				imports.put(functionName, fullyQualified);
			}
		}
		return imports;
	}

	private void fetchWildcardImports(Map<String, String> imports, String library, Collection<String> libraryItems)
			throws PolicyEvaluationException {
		for (var name : libraryItems) {
			if (imports.put(name, String.join(".", library, name)) != null) {
				throw new PolicyEvaluationException(WILDCARD_IMPORT_EXISTS, library, name);
			}
		}
	}

	private void fetchLibraryImports(Map<String, String> imports, String library, String alias,
			Collection<String> libraryItems) {
		for (var name : libraryItems) {
			var key = String.join(".", alias, name);
			if (imports.put(key, String.join(".", library, name)) != null) {
				throw new PolicyEvaluationException(LIBRARY_IMPORT_EXISTS, library, name);
			}
		}
	}

	private void logAuthzDecision(AuthorizationDecision r) {
		log.trace("| | |-- {}. Document: {} full: {}", r.getDecision(), getPolicyElement().getSaplName(), r);
	}

}
