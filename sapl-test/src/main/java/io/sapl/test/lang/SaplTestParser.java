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
package io.sapl.test.lang;

import io.sapl.parser.InputStreamHelper;
import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sapltest.SAPLTest;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.XtextResourceSet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Parser for SAPL test files (.sapltest).
 * <p>
 * Parses test definitions into an AST that can be executed by
 * {@link SaplTestRunner}.
 * Includes protection against trojan source attacks via bidirectional Unicode
 * character detection.
 */
@UtilityClass
public class SaplTestParser {

    private static final String DUMMY_RESOURCE_URI = "test:/test.sapltest";

    /**
     * Parses a SAPL test definition from a string.
     *
     * @param testDefinition the test definition source code
     * @return the parsed AST
     * @throws SaplTestException if parsing fails or invalid characters are
     * detected
     */
    public static SAPLTest parse(String testDefinition) {
        return parse(new ByteArrayInputStream(testDefinition.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Parses a SAPL test definition from an input stream.
     * <p>
     * The stream is processed to detect encoding and protect against trojan source
     * attacks.
     *
     * @param inputStream the input stream containing the test definition
     * @return the parsed AST
     * @throws SaplTestException if parsing fails or invalid characters are
     * detected
     */
    public static SAPLTest parse(InputStream inputStream) {
        try {
            var securedStream = secureInputStream(inputStream);
            return loadResource(securedStream);
        } catch (IOException e) {
            throw new SaplTestException("Failed to read test definition.", e);
        }
    }

    private static InputStream secureInputStream(InputStream inputStream) throws IOException {
        var converted = InputStreamHelper.detectAndConvertEncodingOfStream(inputStream);
        return InputStreamHelper.convertToTrojanSourceSecureStream(converted);
    }

    private static SAPLTest loadResource(InputStream inputStream) {
        var injector    = SAPLTestStandaloneSetup.doSetupAndGetInjector();
        var resourceSet = injector.getInstance(XtextResourceSet.class);
        var resource    = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

        try {
            resource.load(inputStream, resourceSet.getLoadOptions());
        } catch (IOException e) {
            throw new SaplTestException("Failed to load test resource.", e);
        }

        if (!resource.getErrors().isEmpty()) {
            var errors = resource.getErrors().stream().map(error -> error.getMessage() + " at line " + error.getLine())
                    .toList();
            throw new SaplTestException("Parsing errors: " + errors);
        }

        return (SAPLTest) resource.getContents().getFirst();
    }
}
