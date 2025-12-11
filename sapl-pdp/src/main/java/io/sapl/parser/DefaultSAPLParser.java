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
package io.sapl.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.google.inject.Guice;
import com.google.inject.Injector;

import io.sapl.grammar.SAPLRuntimeModule;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.SAPL;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResourceSet;

/**
 * The default implementation of the SAPLParser interface.
 * <p>
 * This parser uses lazy initialization for the Guice injector to avoid
 * conflicts with web-based SAPL editors. If an EMF
 * registration for the SAPL language already exists (e.g., from SAPLWebSetup
 * with IDE bindings), this parser will
 * create a minimal injector without overwriting the existing registration,
 * preserving content assist and other IDE
 * features.
 * </p>
 */
@Slf4j
public class DefaultSAPLParser implements SAPLParser {

    public static final String INVALID_BYTE_SEQUENCE = "Invalid byte sequence in InputStream.";

    private static final String                    DUMMY_RESOURCE_URI = "policy:/aPolicy.sapl";
    private static final String                    PARSING_ERRORS     = "Parsing errors: %s";
    private static final AtomicReference<Injector> INJECTOR_REF       = new AtomicReference<>();

    /**
     * Lazily initializes the Guice injector, respecting any existing EMF
     * registration.
     * <p>
     * If the SAPL language is already registered in EMF (e.g., by SAPLWebSetup),
     * this method creates a minimal injector
     * without re-registering EMF resources, thus preserving IDE bindings like
     * content assist. If no registration
     * exists, it performs full EMF registration.
     * </p>
     *
     * @return the Guice injector for obtaining SAPL language services
     */
    private static Injector getInjector() {
        return INJECTOR_REF.updateAndGet(existing -> {
            if (existing != null) {
                return existing;
            }

            // Check if EMF registration already exists (from web setup, etc.)
            var existingProvider = IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("sapl");

            if (existingProvider != null) {
                // Someone already set up the SAPL language - create minimal injector
                // WITHOUT re-registering EMF to preserve IDE bindings
                log.debug("SAPL EMF registration already exists, creating minimal injector.");
                return Guice.createInjector(new SAPLRuntimeModule());
            }

            // No existing setup - do full registration
            log.debug("No existing SAPL EMF registration, performing full setup.");
            return new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();
        });
    }

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
        final XtextResourceSet resourceSet = getInjector().getInstance(XtextResourceSet.class);
        final Resource         resource    = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

        try {
            resource.load(policyInputStream, resourceSet.getLoadOptions());
        } catch (IOException | WrappedException e) {
            final var errorMessage = PARSING_ERRORS.formatted(resource.getErrors());
            log.debug(errorMessage, e);
            return new Document(id, null, null, null, errorMessage);
        }

        if (!resource.getErrors().isEmpty()) {
            final var errorMessage = PARSING_ERRORS.formatted(resource.getErrors());
            log.debug(errorMessage);
            return new Document(id, null, null, null, errorMessage);
        }

        final var sapl = (SAPL) resource.getContents().getFirst();
        String    name = null;
        if (sapl != null && sapl.getPolicyElement() != null)
            name = sapl.getPolicyElement().getSaplName();
        final var diagnostic = Diagnostician.INSTANCE.validate(sapl);
        final var actualId   = null == id ? name : null;
        return new Document(actualId, name, sapl, diagnostic, composeErrorMessage(diagnostic));
    }

    @Override
    public SAPL parse(InputStream saplInputStream) {
        final var document = parseDocument(saplInputStream);
        if (document.isInvalid()) {
            throw new SaplParserException(document.errorMessage());
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
        final var sb = new StringBuilder().append("SAPL Validation Error: [");
        for (Diagnostic d : diagnostic.getChildren()) {
            sb.append('[').append(NodeModelUtils.findActualNodeFor((EObject) d.getData().getFirst()).getText())
                    .append(": ").append(d.getMessage()).append(']');
        }
        return sb.append(']').toString();
    }

}
