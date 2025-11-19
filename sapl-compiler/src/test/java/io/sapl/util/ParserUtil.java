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
package io.sapl.util;

import com.google.inject.Injector;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.Entitlement;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FilterComponent;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.services.SAPLGrammarAccess;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class ParserUtil {

    private static final Injector          INJECTOR       = new SAPLStandaloneSetup()
            .createInjectorAndDoEMFRegistration();
    private static final SAPLGrammarAccess GRAMMAR_ACCESS = INJECTOR.getInstance(SAPLGrammarAccess.class);

    public static FilterComponent filterComponent(String sapl) throws IOException {
        return parseFragment(sapl, GRAMMAR_ACCESS.getFilterComponentRule());
    }

    public static Expression expression(String sapl) throws IOException {
        return parseFragment(sapl, GRAMMAR_ACCESS.getExpressionRule());
    }

    public static Statement statement(String sapl) throws IOException {
        return parseFragment(sapl, GRAMMAR_ACCESS.getStatementRule());
    }

    public static Entitlement entitlement(String sapl) throws IOException {
        return parseFragment(sapl, GRAMMAR_ACCESS.getEntitlementRule());
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseFragment(String sapl, ParserRule rule) throws IOException {
        val resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
        val resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
        resource.setEntryPoint(rule);
        val inputStream = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
        resource.load(inputStream, resourceSet.getLoadOptions());

        // Throw exception if parse errors exist
        // This prevents silent failures where invalid SAPL syntax like {fresh=true}
        // gets parsed as {} without any error, leading to confusing runtime behavior.
        if (!resource.getErrors().isEmpty()) {
            val errorMessages = new StringBuilder("Parse errors:\n");
            for (val error : resource.getErrors()) {
                errorMessages.append("  - ").append(error.getMessage()).append("\n");
            }
            throw new IOException(errorMessages.toString());
        }

        return (T) resource.getContents().getFirst();
    }

}
