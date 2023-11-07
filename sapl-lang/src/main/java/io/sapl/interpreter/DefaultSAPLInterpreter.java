/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Injector;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import reactor.core.publisher.Flux;

/**
 * The default implementation of the SAPLInterpreter interface.
 */
public class DefaultSAPLInterpreter implements SAPLInterpreter {

    private static final String DUMMY_RESOURCE_URI = "policy:/aPolicy.sapl";

    private static final String PARSING_ERRORS = "Parsing errors: %s";

    private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

    @Override
    public SAPL parse(String saplDefinition) {
        return parse(new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public SAPL parse(InputStream saplInputStream) {
        var sapl       = loadAsResource(saplInputStream);
        var diagnostic = Diagnostician.INSTANCE.validate(sapl);
        if (diagnostic.getSeverity() == Diagnostic.OK)
            return sapl;

        throw new PolicyEvaluationException(composeReason(diagnostic));
    }

    private String composeReason(Diagnostic diagnostic) {
        var sb = new StringBuilder().append("SAPL Validation Error: [");
        for (Diagnostic d : diagnostic.getChildren()) {
            sb.append('[').append(NodeModelUtils.findActualNodeFor((EObject) d.getData().get(0)).getText()).append(": ")
                    .append(d.getMessage()).append(']');
        }
        return sb.append(']').toString();
    }

    @Override
    public Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authzSubscription, String saplDocumentSource,
            AttributeContext attributeContext, FunctionContext functionContext,
            Map<String, JsonNode> environmentVariables) {
        final SAPL saplDocument;
        try {
            saplDocument = parse(saplDocumentSource);
        } catch (PolicyEvaluationException e) {
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }
        return saplDocument.matches().flux().switchMap(evaluateBodyIfMatching(saplDocument))
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, environmentVariables))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeContext(ctx, attributeContext))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext))
                .onErrorReturn(AuthorizationDecision.INDETERMINATE);
    }

    private Function<? super Val, Publisher<? extends AuthorizationDecision>> evaluateBodyIfMatching(
            final SAPL saplDocument) {
        return match -> {
            if (match.isError())
                return Flux.just(AuthorizationDecision.INDETERMINATE);
            if (match.getBoolean()) {
                return saplDocument.evaluate().map(DocumentEvaluationResult::getAuthorizationDecision);
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
        final Resource         resource    = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

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
