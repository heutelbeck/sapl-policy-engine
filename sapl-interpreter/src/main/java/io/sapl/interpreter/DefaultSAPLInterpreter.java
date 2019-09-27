/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Injector;

import io.sapl.api.interpreter.DocumentAnalysisResult;
import io.sapl.api.interpreter.DocumentType;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * The default implementation of the SAPLInterpreter interface.
 */
@Slf4j
public class DefaultSAPLInterpreter implements SAPLInterpreter {

	private static final Response INDETERMINATE = Response.INDETERMINATE;

	private static final String DUMMY_RESOURCE_URI = "policy:/apolicy.sapl";

	static final String POLICY_EVALUATION_FAILED = "Policy evaluation failed: {}";
	static final String PARSING_ERRORS = "Parsing errors: %s";

	private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

	@Override
	public SAPL parse(String saplDefinition) throws PolicyEvaluationException {
		return (SAPL) loadAsResource(saplDefinition).getContents().get(0);
	}

	@Override
	public SAPL parse(InputStream saplInputStream) throws PolicyEvaluationException {
		return (SAPL) loadAsResource(saplInputStream).getContents().get(0);
	}

	private static Resource loadAsResource(InputStream policyInputStream) throws PolicyEvaluationException {
		final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		final Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

		try {
			resource.load(policyInputStream, resourceSet.getLoadOptions());
		}
		catch (IOException | WrappedException e) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()), e);
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()));
		}
		return resource;
	}

	private static Resource loadAsResource(String saplDefinition) throws PolicyEvaluationException {
		final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
		final Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

		try (InputStream in = new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8))) {
			resource.load(in, resourceSet.getLoadOptions());
		}
		catch (IOException e) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()), e);
		}

		if (!resource.getErrors().isEmpty()) {
			throw new PolicyEvaluationException(String.format(PARSING_ERRORS, resource.getErrors()));
		}
		return resource;
	}

	@Override
	public Flux<Response> evaluate(Request request, String saplDefinition, AttributeContext attributeCtx,
			FunctionContext functionCtx, Map<String, JsonNode> systemVariables) {
		final SAPL saplDocument;
		try {
			saplDocument = parse(saplDefinition);
		}
		catch (PolicyEvaluationException e) {
			LOGGER.error("Error in policy parsing: {}", e.getMessage());
			return Flux.just(INDETERMINATE);
		}

		try {
			final VariableContext variableCtx = new VariableContext(request, systemVariables);
			final EvaluationContext evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variableCtx);
			return saplDocument.evaluate(evaluationCtx).onErrorReturn(INDETERMINATE);
		}
		catch (PolicyEvaluationException e) {
			LOGGER.trace("| | |-- INDETERMINATE. Cause: " + POLICY_EVALUATION_FAILED, e.getMessage());
			LOGGER.trace("| |");
			return Flux.just(INDETERMINATE);
		}
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
			}
			else {
				Policy policy = (Policy) sapl.getPolicyElement();
				result = new DocumentAnalysisResult(true, policy.getSaplName(), DocumentType.POLICY, "");
			}
		}
		catch (PolicyEvaluationException e) {
			result = new DocumentAnalysisResult(false, "", null, e.getMessage());
		}
		return result;
	}

}
