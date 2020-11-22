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
package io.sapl.interpreter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.reactivestreams.Publisher;

import com.google.inject.Injector;

import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * The default implementation of the SAPLInterpreter interface.
 */
@Slf4j
public class DefaultSAPLInterpreter implements SAPLInterpreter {

	private static final String DUMMY_RESOURCE_URI = "policy:/apolicy.sapl";
	private static final String PARSING_ERRORS = "Parsing errors: %s";

	private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

	@Override
	public SAPL parse(String saplDefinition) {
		return parse(new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8)));
	}

	@Override
	public SAPL parse(InputStream saplInputStream) {
		return loadAsResource(saplInputStream);
	}

	@Override
	public Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authzSubscription, String saplDefinition,
			EvaluationContext evaluationCtx) {
		final SAPL saplDocument;
		try {
			saplDocument = parse(saplDefinition);
		} catch (PolicyEvaluationException e) {
			log.debug("Error in policy parsing: {}", e.getMessage());
			return Flux.just(AuthorizationDecision.INDETERMINATE);
		}
		var subscriptionScopedEvaluationCtx = evaluationCtx.forAuthorizationSubscription(authzSubscription);
		return saplDocument.matches(subscriptionScopedEvaluationCtx).flux()
				.switchMap(evaluateBodyIfMatching(saplDocument, subscriptionScopedEvaluationCtx));

	}

	private Function<? super Val, Publisher<? extends AuthorizationDecision>> evaluateBodyIfMatching(
			final SAPL saplDocument, EvaluationContext subscriptionScopedEvaluationCtx) {
		return match -> {
			if (match.isError())
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			if (match.getBoolean()) {
				return saplDocument.evaluate(subscriptionScopedEvaluationCtx);
			}
			return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
		};
	}

	@Override
	public DocumentAnalysisResult analyze(String policyDefinition) {
		SAPL saplDocument;
		try {
			saplDocument = parse(policyDefinition);
		} catch (PolicyEvaluationException e) {
			return new DocumentAnalysisResult(false, "", null, e.getMessage());
		}
		return new DocumentAnalysisResult(true, saplDocument.getPolicyElement().getSaplName(),
				typeOfDocument(saplDocument), "");
	}

	private DocumentType typeOfDocument(SAPL saplDocument) {
		return saplDocument.getPolicyElement() instanceof PolicySet ? DocumentType.POLICY_SET : DocumentType.POLICY;
	}

	private static SAPL loadAsResource(InputStream policyInputStream) {
		final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		final Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

		try {
			resource.load(policyInputStream, resourceSet.getLoadOptions());
		} catch (IOException | WrappedException e) {
			throw new PolicyEvaluationException(e, PARSING_ERRORS, resource.getErrors());
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(PARSING_ERRORS, resource.getErrors());
		}
		return (SAPL) resource.getContents().get(0);
	}

}
