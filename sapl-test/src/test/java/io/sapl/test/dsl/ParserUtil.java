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

package io.sapl.test.dsl;

import com.google.inject.Injector;
import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.mockito.ArgumentMatchers;

public class ParserUtil {
    private static final Injector INJECTOR = new SAPLTestStandaloneSetup().createInjectorAndDoEMFRegistration();

    @SneakyThrows
    public static <T extends ParserRule, R> R buildExpression(final String saplTest,
            Function<SAPLTestGrammarAccess, T> resolver) {
        var       resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
        var       resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("test:/default.sapltest"));
        final var parserRule  = resolver.apply(INJECTOR.getInstance(SAPLTestGrammarAccess.class));
        resource.setEntryPoint(parserRule);
        resource.load(IOUtils.toInputStream(saplTest, StandardCharsets.UTF_8), resourceSet.getLoadOptions());
        return (R) resource.getContents().get(0);
    }

    public static <T extends Value> T buildValue(final String input) {
        return buildExpression(input, SAPLTestGrammarAccess::getValueRule);
    }

    public static Value compareArgumentToStringLiteral(final String other) {
        return ArgumentMatchers.argThat(value -> value instanceof StringLiteral stringLiteral && stringLiteral
                .getString().equals(ParserUtil.<StringLiteral>buildValue("\"%s\"".formatted(other)).getString()));
    }
}
