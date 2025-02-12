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
package io.sapl.test.dsl.setup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResourceSet;

import io.sapl.interpreter.InputStreamHelper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sapltest.SAPLTest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultSaplTestInterpreter implements SaplTestInterpreter {
    public static final String INVALID_BYTE_SEQUENCE = "Invalid byte sequence in InputStream.";

    private static final String DUMMY_RESOURCE_URI = "test:/test1.sapltest";
    private static final String PARSING_ERRORS     = "Parsing errors: %s";

    @Override
    public SAPLTest loadAsResource(final InputStream testInputStream) {
        final var injector    = SAPLTestStandaloneSetup.doSetupAndGetInjector();
        final var resourceSet = injector.getInstance(XtextResourceSet.class);
        final var resource    = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

        try {
            resource.load(testInputStream, resourceSet.getLoadOptions());
        } catch (IOException | WrappedException e) {
            throw new SaplTestException(e);
        }

        if (!resource.getErrors().isEmpty()) {
            throw new SaplTestException("Input is not a valid test definition");
        }
        return (SAPLTest) resource.getContents().get(0);
    }

    @Override
    public SAPLTest loadAsResource(final String input) {
        final var inputStream = IOUtils.toInputStream(input, StandardCharsets.UTF_8);
        return loadAsResource(inputStream);
    }

    @Override
    public TestDocument parseDocument(String source) {
        return parseDocument(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    }

    public TestDocument parseDocument(final InputStream saplInputStream) {
        InputStream convertedAndSecuredInputStream;
        try {
            convertedAndSecuredInputStream = InputStreamHelper.detectAndConvertEncodingOfStream(saplInputStream);
            convertedAndSecuredInputStream = InputStreamHelper
                    .convertToTrojanSourceSecureStream(convertedAndSecuredInputStream);
        } catch (IOException e) {
            return new TestDocument(null, null, INVALID_BYTE_SEQUENCE);
        }
        return loadDocumentAsResource(convertedAndSecuredInputStream);
    }

    public TestDocument loadDocumentAsResource(final InputStream testInputStream) {
        final var injector    = SAPLTestStandaloneSetup.doSetupAndGetInjector();
        final var resourceSet = injector.getInstance(XtextResourceSet.class);
        final var resource    = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

        try {
            resource.load(testInputStream, resourceSet.getLoadOptions());
        } catch (IOException | WrappedException e) {
            final var errorMessage = String.format(PARSING_ERRORS, resource.getErrors());
            log.debug(errorMessage, e);
            return new TestDocument(null, null, errorMessage);
        }

        if (!resource.getErrors().isEmpty()) {
            final var errorMessage = String.format(PARSING_ERRORS, resource.getErrors());
            return new TestDocument(null, null, errorMessage);
        }

        final var saplTest   = (SAPLTest) resource.getContents().get(0);
        final var diagnostic = Diagnostician.INSTANCE.validate(saplTest);

        return new TestDocument(saplTest, diagnostic, composeErrorMessage(diagnostic));
    }

    private static String composeErrorMessage(Diagnostic diagnostic) {
        if (diagnostic.getSeverity() == Diagnostic.OK) {
            return "OK";
        }
        final var sb = new StringBuilder().append("SAPLTest Validation Error: [");
        for (Diagnostic d : diagnostic.getChildren()) {
            sb.append('[').append(NodeModelUtils.findActualNodeFor((EObject) d.getData().get(0)).getText()).append(": ")
                    .append(d.getMessage()).append(']');
        }
        return sb.append(']').toString();
    }

}
