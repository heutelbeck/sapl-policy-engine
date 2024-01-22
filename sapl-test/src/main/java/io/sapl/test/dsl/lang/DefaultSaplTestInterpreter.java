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

package io.sapl.test.dsl.lang;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sapltest.SAPLTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.xtext.resource.XtextResourceSet;

public final class DefaultSaplTestInterpreter implements SaplTestInterpreter {
    private static final String DUMMY_RESOURCE_URI = "test:/test1.sapltest";

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
}
