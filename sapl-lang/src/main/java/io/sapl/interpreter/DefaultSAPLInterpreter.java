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
package io.sapl.interpreter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;

import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.Policy;
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
		return (SAPL) loadAsResource(saplDefinition).getContents().get(0);
	}

	@Override
	public SAPL parse(InputStream saplInputStream) {
		return (SAPL) loadAsResource(saplInputStream).getContents().get(0);
	}

	private static Resource loadAsResource(InputStream policyInputStream) {
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
		return resource;
	}

	private static Resource loadAsResource(String saplDefinition) {
		final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		final Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));
		try (InputStream in = new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8))) {
			resource.load(in, resourceSet.getLoadOptions());
		} catch (IOException e) {
			throw new PolicyEvaluationException(e, PARSING_ERRORS, resource.getErrors());
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(PARSING_ERRORS, resource.getErrors());
		}
		return resource;
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
		return saplDocument.evaluate(subscriptionScopedEvaluationCtx);
	}

	@Override
	public DocumentAnalysisResult analyze(String policyDefinition) {
		DocumentAnalysisResult result;
		try {
			Resource resource = loadAsResource(policyDefinition);
			SAPL sapl = (SAPL) resource.getContents().get(0);
			if (sapl.getPolicyElement() instanceof PolicySet) {
				PolicySet set = (PolicySet) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, set.getSaplName(), DocumentType.POLICY_SET, "");
			} else {
				Policy policy = (Policy) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, policy.getSaplName(), DocumentType.POLICY, "");
			}
		} catch (PolicyEvaluationException e) {
			result = new DocumentAnalysisResult(false, "", null, e.getMessage());
		}
		return result;
	}

}
