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

package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.dsl.interfaces.IntegrationTestConfiguration;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.DocumentSet;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

@ExtendWith(MockitoExtension.class)
class DocumentInterpreterTests {
    protected final MockedStatic<SaplUnitTestFixtureFactory>        saplUnitTestFixtureFactoryMockedStatic        = mockStatic(
            SaplUnitTestFixtureFactory.class);
    protected final MockedStatic<SaplIntegrationTestFixtureFactory> saplIntegrationTestFixtureFactoryMockedStatic = mockStatic(
            SaplIntegrationTestFixtureFactory.class);

    protected DocumentInterpreter documentInterpreter;

    @BeforeEach
    void setUp() {
        documentInterpreter = new DocumentInterpreter(null, null);
    }

    @AfterEach
    void tearDown() {
        saplUnitTestFixtureFactoryMockedStatic.close();
        saplIntegrationTestFixtureFactoryMockedStatic.close();
    }

    private Document buildDocument(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getDocumentRule, Document.class);
    }

    private void constructTestSuiteInterpreterWithCustomResolvers(
            final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        documentInterpreter = new DocumentInterpreter(customUnitTestPolicyResolver,
                customIntegrationTestPolicyResolver);
    }

    @Test
    void getFixtureFromDocument_handlesNullDocument_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> documentInterpreter.getFixtureFromDocument(null));

        assertEquals("No Document available", exception.getMessage());
    }

    @Nested
    @DisplayName("Unit test cases")
    class UnitTestCases {
        @Test
        void getFixtureFromDocument_handlesDefaultUnitTestPolicyResolver_returnsSaplUnitTestFixture() {
            final var document = buildDocument("policy \"fooPolicy\"");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic.when(() -> SaplUnitTestFixtureFactory.create("fooPolicy"))
                    .thenReturn(saplUnitTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplUnitTestFixtureMock, result);
        }

        @Test
        void getFixtureFromDocument_handlesCustomUnitTestPolicyResolver_returnsSaplUnitTestFixture() {
            final var customUnitTestPolicyResolver = mock(UnitTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(customUnitTestPolicyResolver, null);

            final var document = buildDocument("policy \"fooPolicy\"");

            when(customUnitTestPolicyResolver.resolvePolicyByIdentifier("fooPolicy")).thenReturn("resolvedPolicy");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic
                    .when(() -> SaplUnitTestFixtureFactory.createFromInputString("resolvedPolicy"))
                    .thenReturn(saplUnitTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplUnitTestFixtureMock, result);
        }
    }

    @Nested
    @DisplayName("Integration test cases")
    class IntegrationTestCases {
        @Test
        void getFixtureFromDocument_handlesUnknownDocument_throwsSaplTestException() {
            final var unknownDocumentMock = mock(Document.class);

            final var exception = assertThrows(SaplTestException.class,
                    () -> documentInterpreter.getFixtureFromDocument(unknownDocumentMock));

            assertEquals("Unknown type of Document", exception.getMessage());
        }

        @Test
        void getFixtureFromDocument_handlesNullIdentifiersForDocumentSet_throwsSaplTestException() {
            final var documentSetMock = mock(DocumentSet.class);

            when(documentSetMock.getIdentifiers()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> documentInterpreter.getFixtureFromDocument(documentSetMock));

            assertEquals("No policies to test integration for", exception.getMessage());
        }

        @ParameterizedTest
        @MethodSource("invalidListOfIdentifiers")
        void getFixtureFromDocument_handlesInvalidAmountOfIdentifiersForDocumentSet_throwsSaplTestException(
                final List<String> policies) {
            final var documentSetMock = mock(DocumentSet.class);

            TestHelper.mockEListResult(documentSetMock::getIdentifiers, policies);

            final var exception = assertThrows(SaplTestException.class,
                    () -> documentInterpreter.getFixtureFromDocument(documentSetMock));

            assertEquals("No policies to test integration for", exception.getMessage());
        }

        @Test
        void getFixtureFromDocument_handlesNullIdentifiersForDocumentSetWithCustomIntegrationTestPolicyResolver_throwsSaplTestException() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);
            final var documentSetMock = mock(DocumentSet.class);

            when(documentSetMock.getIdentifiers()).thenReturn(null);

            final var exception = assertThrows(SaplTestException.class,
                    () -> documentInterpreter.getFixtureFromDocument(documentSetMock));

            assertEquals("No policies to test integration for", exception.getMessage());
        }

        @ParameterizedTest
        @MethodSource("invalidListOfIdentifiers")
        void getFixtureFromDocument_handlesInvalidAmountOfIdentifiersForDocumentSetWithCustomIntegrationTestPolicyResolver_throwsSaplTestException(
                final List<String> policies) {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var documentSetMock = mock(DocumentSet.class);

            TestHelper.mockEListResult(documentSetMock::getIdentifiers, policies);

            final var exception = assertThrows(SaplTestException.class,
                    () -> documentInterpreter.getFixtureFromDocument(documentSetMock));

            assertEquals("No policies to test integration for", exception.getMessage());
        }

        private static Stream<Arguments> invalidListOfIdentifiers() {
            return Stream.of(Arguments.of(Collections.emptyList()), Arguments.of(List.of("singlePolicy")));
        }

        @Test
        void getFixtureFromDocument_handlesPdpConfigurationIdentifierForDocumentSet_returnsSaplIntegrationTestFixture() {
            final var document = buildDocument("policies \"policy1\",\"policy2\" with pdp configuration \"fooFolder\"");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder", List.of("policy1", "policy2")))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromDocument_handlesPdpConfigurationIdentifierForDocumentSetWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var document = buildDocument("policies \"policy1\",\"policy2\" with pdp configuration \"fooFolder\"");

            when(integrationTestPolicyResolver.resolvePDPConfigurationByIdentifier("fooFolder"))
                    .thenReturn("resolvedPdpConfig");

            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy1")).thenReturn("resolvedPolicy1");
            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy2")).thenReturn("resolvedPolicy2");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory
                            .createFromInputStrings(List.of("resolvedPolicy1", "resolvedPolicy2"), "resolvedPdpConfig"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromDocument_handlesNullPdpConfigurationIdentifierForDocumentSetWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var document = buildDocument("policies \"policy1\",\"policy2\"");

            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy1")).thenReturn("resolvedPolicy1");
            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy2")).thenReturn("resolvedPolicy2");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory
                            .createFromInputStrings(List.of("resolvedPolicy1", "resolvedPolicy2"), null))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
            verify(integrationTestPolicyResolver, never()).resolvePDPConfigurationByIdentifier(null);
        }

        @Test
        void getFixtureFromDocument_handlesIdentifierForDocumentSetWithSingleIdentifier_returnsSaplIntegrationTestFixture() {
            final var document = buildDocument("set \"fooFolder\"");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromDocument_handlesIdentifierForDocumentSetWithSingleIdentifierWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var document = buildDocument("set \"fooFolder\"");

            final var integrationTestConfigurationMock = mock(IntegrationTestConfiguration.class);
            when(integrationTestPolicyResolver.resolveConfigurationByIdentifier("fooFolder"))
                    .thenReturn(integrationTestConfigurationMock);

            when(integrationTestConfigurationMock.getDocumentInputStrings()).thenReturn(List.of("policy1", "policy2"));
            when(integrationTestConfigurationMock.getPDPConfigurationInputString()).thenReturn("pdpConfig");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory
                    .createFromInputStrings(List.of("policy1", "policy2"), "pdpConfig"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = documentInterpreter.getFixtureFromDocument(document);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }
    }
}
