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

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.DocumentSet;
import io.sapl.test.grammar.sapltest.DocumentSetWithSingleIdentifier;
import io.sapl.test.grammar.sapltest.SingleDocument;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DocumentInterpreter {

    private final UnitTestPolicyResolver        customUnitTestPolicyResolver;
    private final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver;

    SaplTestFixture getFixtureFromDocument(final Document document) {
        SaplTestFixture saplTestFixture;

        if (document == null) {
            throw new SaplTestException("No Document available");
        }

        if (document instanceof SingleDocument singleDocument) {
            saplTestFixture = getUnitTestFixtureFromSingleDocument(singleDocument);
        } else {
            saplTestFixture = getIntegrationTestFixtureFromSetOfDocuments(document);
        }

        return saplTestFixture;
    }

    private SaplTestFixture getUnitTestFixtureFromSingleDocument(final SingleDocument singleDocument) {
        if (customUnitTestPolicyResolver == null) {
            return SaplUnitTestFixtureFactory.create(singleDocument.getIdentifier());
        } else {
            return SaplUnitTestFixtureFactory.createFromInputString(
                    customUnitTestPolicyResolver.resolvePolicyByIdentifier(singleDocument.getIdentifier()));
        }
    }

    private SaplTestFixture getIntegrationTestFixtureFromSetOfDocuments(final Document document) {
        SaplIntegrationTestFixture integrationTestFixture;

        if (document instanceof DocumentSetWithSingleIdentifier documentSetWithSingleIdentifier) {
            integrationTestFixture = handleDocumentSetWithSingleIdentifier(documentSetWithSingleIdentifier);
        } else if (document instanceof DocumentSet documentSet) {
            integrationTestFixture = handleDocumentSet(documentSet);
        } else {
            throw new SaplTestException("Unknown type of Document");
        }

        return integrationTestFixture;
    }

    private SaplIntegrationTestFixture handleDocumentSetWithSingleIdentifier(
            final DocumentSetWithSingleIdentifier documentSetWithSingleIdentifier) {
        SaplIntegrationTestFixture integrationTestFixture;
        final var                  identifier = documentSetWithSingleIdentifier.getIdentifier();

        if (customIntegrationTestPolicyResolver == null) {
            integrationTestFixture = SaplIntegrationTestFixtureFactory.create(identifier);
        } else {
            final var config = customIntegrationTestPolicyResolver.resolveConfigurationByIdentifier(identifier);

            integrationTestFixture = SaplIntegrationTestFixtureFactory
                    .createFromInputStrings(config.getDocumentInputStrings(), config.getPDPConfigurationInputString());
        }
        return integrationTestFixture;
    }

    private SaplIntegrationTestFixture handleDocumentSet(final DocumentSet documentSet) {
        SaplIntegrationTestFixture integrationTestFixture;
        var                        pdpConfig   = documentSet.getPdpConfigurationIdentifier();
        final var                  identifiers = documentSet.getIdentifiers();

        if (identifiers == null || identifiers.size() < 2) {
            throw new SaplTestException("No policies to test integration for");
        }

        if (customIntegrationTestPolicyResolver == null) {
            integrationTestFixture = SaplIntegrationTestFixtureFactory.create(pdpConfig, identifiers);
        } else {
            final var saplDocumentStrings = identifiers.stream()
                    .map(customIntegrationTestPolicyResolver::resolvePolicyByIdentifier).toList();

            if (pdpConfig != null) {
                pdpConfig = customIntegrationTestPolicyResolver.resolvePDPConfigurationByIdentifier(pdpConfig);
            }
            integrationTestFixture = SaplIntegrationTestFixtureFactory.createFromInputStrings(saplDocumentStrings,
                    pdpConfig);
        }
        return integrationTestFixture;
    }
}
