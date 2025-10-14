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
package io.sapl.pdp.interceptors;

import com.google.inject.Injector;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.services.SAPLGrammarAccess;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.pdp.interceptors.ErrorReportGenerator.OutputFormat;
import lombok.SneakyThrows;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorReportGeneratorTests {
    private static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();

    private static final String ANSI_ERROR_ON  = "\u001B[31m\u001B[7m";
    private static final String ANSI_ERROR_OFF = "\u001B[0m";
    private static final String HTML_ERROR_ON  = "<span style=\"color: red\">";
    private static final String HTML_ERROR_OFF = "</span>";

    @Test
    void whenError_then_characterAreEscapedForHTML() throws InitializationException {
        final var documents = List.of("""
                policy "something&to escape"
                permit where <unknown.pip> == "test";
                """);

        final var pdp                       = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(documents,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        final var authorizationSubscription = AuthorizationSubscription.of("willi", "eat", "ice cream");

        final var decision = pdp.decideTraced(authorizationSubscription).blockFirst();
        final var errors   = decision.getErrorsFromTrace();

        final var error1 = errors.iterator().next();
        assertThatVal(error1).isError();
        final var errorReport1 = ErrorReportGenerator.errorReport(error1, true, OutputFormat.HTML);
        assertThat(errorReport1).contains("&quot;").contains("</div>");
    }

    @Test
    void whenError_then_ansiReportMarksRegion() throws InitializationException {
        final var documents = List.of("""
                policy "some policy"
                permit true == false
                     | "X" != 1 / 0
                     | 123 != "123" where true;
                """);

        final var pdp                       = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(documents,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        final var authorizationSubscription = AuthorizationSubscription.of("willi", "eat", "ice cream");

        final var decision = pdp.decideTraced(authorizationSubscription).blockFirst();
        final var errors   = decision.getErrorsFromTrace();

        assertThat(errors).hasSize(2);
        final var iter   = errors.iterator();
        final var error1 = iter.next();
        assertThatVal(error1).isError();
        final var error2 = iter.next();
        assertThatVal(error2).isError();

        final var errorReport1 = ErrorReportGenerator.errorReport(error1, false, OutputFormat.ANSI_TEXT);
        assertThat(errorReport1).contains(ANSI_ERROR_ON + "     | \"X\" != 1 / 0" + ANSI_ERROR_OFF);

        final var errorReport2 = ErrorReportGenerator.errorReport(error2, true, OutputFormat.ANSI_TEXT);
        assertThat(errorReport2).contains(ANSI_ERROR_ON + "1 / 0" + ANSI_ERROR_OFF);
    }

    @Test
    void whenError_then_htmlReportMarksRegion() throws InitializationException {
        final var documents = List.of("""
                policy "some policy"
                permit true == false
                     | "X" != 1 / 0
                     | 123 != "123" where true;
                """);

        final var pdp                       = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(documents,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        final var authorizationSubscription = AuthorizationSubscription.of("willi", "eat", "ice cream");

        final var decision = pdp.decideTraced(authorizationSubscription).blockFirst();
        final var errors   = decision.getErrorsFromTrace();

        assertThat(errors).hasSize(2);
        final var iter   = errors.iterator();
        final var error1 = iter.next();
        assertThatVal(error1).isError();
        final var error2 = iter.next();
        assertThatVal(error2).isError();

        final var errorReport2 = ErrorReportGenerator.errorReport(error2, true, OutputFormat.HTML);
        assertThat(errorReport2).contains(HTML_ERROR_ON + "1 / 0" + HTML_ERROR_OFF);
    }

    @Test
    void whenError_then_plainTextReportMarksRegion() throws InitializationException {
        final var documents = List.of("""
                policy "some policy"
                permit true == false
                     | "X" != 1 / 0
                     | 123 != "123" where true;
                """);

        final var pdp                       = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(documents,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        final var authorizationSubscription = AuthorizationSubscription.of("willi", "eat", "ice cream");

        final var decision = pdp.decideTraced(authorizationSubscription).blockFirst();
        final var errors   = decision.getErrorsFromTrace();

        assertThat(errors).hasSize(2);
        final var iter   = errors.iterator();
        final var error1 = iter.next();
        assertThatVal(error1).isError();
        final var error2 = iter.next();
        assertThatVal(error2).isError();

        final var errorReport1 = ErrorReportGenerator.errorReport(error1, false, OutputFormat.PLAIN_TEXT);
        assertThat(errorReport1).contains("     | \"X\" != 1 / 0\n-------------------\n");

        final var errorReport2 = ErrorReportGenerator.errorReport(error2, true, OutputFormat.PLAIN_TEXT);
        assertThat(errorReport2).contains("3|     | \"X\" != 1 / 0\n |              ^---^");
    }

    @Test
    void when_noError_then_reportedSo() {
        final var noError     = Val.TRUE;
        final var errorReport = ErrorReportGenerator.errorReport(noError, true, OutputFormat.PLAIN_TEXT);
        assertThat(errorReport).isEqualTo("No error");
    }

    @Test
    void when_errorNoSource_then_reportedSo() {
        final var noError     = Val.error("X");
        final var errorReport = ErrorReportGenerator.errorReport(noError, true, OutputFormat.PLAIN_TEXT);
        assertThat(errorReport).isEqualTo("Error: X");
    }

    @Test
    void when_errorSapl_then_noName() {
        final var result      = expression("1/0").evaluate().blockFirst();
        final var errorReport = ErrorReportGenerator.errorReport(result, true, OutputFormat.PLAIN_TEXT);
        assertThat(errorReport).contains("Error in document at (1,1)");
    }

    @SneakyThrows
    public static Expression expression(String sapl) {
        final var resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
        final var resource    = (XtextResource) resourceSet.createResource(URI.createFileURI("policy:/default.sapl"));
        resource.setEntryPoint(INJECTOR.getInstance(SAPLGrammarAccess.class).getExpressionRule());
        InputStream in = new ByteArrayInputStream(sapl.getBytes(StandardCharsets.UTF_8));
        resource.load(in, resourceSet.getLoadOptions());
        return (Expression) resource.getContents().get(0);
    }

}
