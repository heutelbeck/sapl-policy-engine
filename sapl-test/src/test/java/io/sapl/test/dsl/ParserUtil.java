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
package io.sapl.test.dsl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.mockito.ArgumentMatchers;

import com.google.inject.Injector;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.sapltest.Value;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;

public class ParserUtil {
    private static final Injector INJECTOR = new SAPLTestStandaloneSetup().createInjectorAndDoEMFRegistration();

    public static <T extends ParserRule, R> R parseInputByRule(final String saplTest,
            Function<SAPLTestGrammarAccess, T> resolver, Class<R> clazz) {
        final var resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
        final var resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("test:/default.sapltest"));
        final var parserRule  = resolver.apply(INJECTOR.getInstance(SAPLTestGrammarAccess.class));
        resource.setEntryPoint(parserRule);

        try {
            resource.load(IOUtils.toInputStream(saplTest, StandardCharsets.UTF_8), resourceSet.getLoadOptions());
        } catch (IOException e) {
            return null;
        }

        try {
            final var eObject = resource.getContents().get(0);
            return clazz.cast(eObject);
        } catch (IndexOutOfBoundsException | ClassCastException e) {
            throw new SaplTestException("input '%s' does not represent a valid definition for class '%s'"
                    .formatted(saplTest, clazz.getName()), e);
        }
    }

    public static StringLiteral buildStringLiteral(final String input) {
        return parseInputByRule(input, SAPLTestGrammarAccess::getValueRule, StringLiteral.class);
    }

    public static <T extends Value> T buildValue(final String input, Class<T> clazz) {
        return parseInputByRule(input, SAPLTestGrammarAccess::getValueRule, clazz);
    }

    public static Value compareArgumentToStringLiteral(final String other) {
        return ArgumentMatchers.argThat(value -> value instanceof StringLiteral stringLiteral && stringLiteral
                .getString().equals(ParserUtil.buildStringLiteral("\"%s\"".formatted(other)).getString()));
    }
}
