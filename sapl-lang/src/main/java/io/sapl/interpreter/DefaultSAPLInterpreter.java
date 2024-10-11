/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.google.inject.Injector;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.prp.Document;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * The default implementation of the SAPLInterpreter interface.
 */
@Slf4j
public class DefaultSAPLInterpreter implements SAPLInterpreter {

    public static final String INVALID_BYTE_SEQUENCE = "Invalid byte sequence in InputStream.";

    private static final String   DUMMY_RESOURCE_URI = "policy:/aPolicy.sapl";
    private static final String   PARSING_ERRORS     = "Parsing errors: %s";
    private static final Injector INJECTOR           = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

    @Override
    public Document parseDocument(final String id, final InputStream saplInputStream) {
        InputStream convertedAndSecuredInputStream;
        try {
            convertedAndSecuredInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(saplInputStream);
            convertedAndSecuredInputStream = InputStreamHelper
                    .convertToTrojanSourceSecureStream(convertedAndSecuredInputStream);
        } catch (IOException e) {
            return new Document(id, null, null, null, INVALID_BYTE_SEQUENCE);
        }
        return loadAsResource(id, convertedAndSecuredInputStream);
    }

    private static Document loadAsResource(String id, InputStream policyInputStream) {
        final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
        final Resource         resource    = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

        try {
            resource.load(policyInputStream, resourceSet.getLoadOptions());
        } catch (IOException | WrappedException e) {
            var errorMessage = String.format(PARSING_ERRORS, resource.getErrors());
            log.debug(errorMessage, e);
            return new Document(id, null, null, null, errorMessage);
        }

        if (!resource.getErrors().isEmpty()) {
            var errorMessage = String.format(PARSING_ERRORS, resource.getErrors());
            log.debug(errorMessage);
            return new Document(id, null, null, null, errorMessage);
        }

        var    sapl = (SAPL) resource.getContents().get(0);
        String name = null;
        if (sapl != null && sapl.getPolicyElement() != null)
            name = sapl.getPolicyElement().getSaplName();
        var diagnostic = Diagnostician.INSTANCE.validate(sapl);
        var actualId   = null == id ? name : null;
        return new Document(actualId, name, sapl, diagnostic, composeErrorMessage(diagnostic));
    }

    @Override
    public SAPL parse(InputStream saplInputStream) {
        var document = parseDocument(saplInputStream);
        if (document.isInvalid()) {
            throw new PolicyEvaluationException(document.errorMessage());
        }
        return document.sapl();
    }

    @Override
    public Document parseDocument(String saplDefinition) {
        return parseDocument(new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Document parseDocument(InputStream saplInputStream) {
        return parseDocument(null, saplInputStream);
    }

    @Override
    public Document parseDocument(final String id, final String saplDefinition) {
        return parseDocument(id, new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public SAPL parse(String saplDefinition) {
        return parse(new ByteArrayInputStream(saplDefinition.getBytes(StandardCharsets.UTF_8)));
    }

    private static String composeErrorMessage(Diagnostic diagnostic) {
        if (diagnostic.getSeverity() == Diagnostic.OK) {
            return "OK";
        }
        var sb = new StringBuilder().append("SAPL Validation Error: [");
        for (Diagnostic d : diagnostic.getChildren()) {
            sb.append('[').append(NodeModelUtils.findActualNodeFor((EObject) d.getData().get(0)).getText()).append(": ")
                    .append(d.getMessage()).append(']');
        }
        return sb.append(']').toString();
    }

    @Override
    public Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authorizationSubscription,
            String saplDocumentSource, AttributeContext attributeContext, FunctionContext functionContext,
            Map<String, Val> environmentVariables) {
        var document = parseDocument(saplDocumentSource);
        if (document.isInvalid()) {
            return Flux.just(AuthorizationDecision.INDETERMINATE);
        }
        var saplDocument = document.sapl();
        return saplDocument.matches().flux().switchMap(evaluateBodyIfMatching(saplDocument))
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, environmentVariables))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authorizationSubscription))
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

}
