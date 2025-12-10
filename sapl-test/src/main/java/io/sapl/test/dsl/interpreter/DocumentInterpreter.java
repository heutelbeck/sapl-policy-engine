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
package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sapltest.Document;
import io.sapl.test.grammar.sapltest.SingleDocument;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DocumentInterpreter {

    private final UnitTestPolicyResolver customUnitTestPolicyResolver;

    SaplTestFixture getFixtureFromDocument(final Document document) {
        if (document == null) {
            throw new SaplTestException("No Document available");
        }

        if (document instanceof SingleDocument singleDocument) {
            return getUnitTestFixtureFromSingleDocument(singleDocument);
        }

        // Integration tests are not yet supported in the new compiler API
        throw new SaplTestException(
                "Integration tests (multiple documents) are not yet supported. Please use unit tests with single documents.");
    }

    private SaplTestFixture getUnitTestFixtureFromSingleDocument(final SingleDocument singleDocument) {
        if (customUnitTestPolicyResolver == null) {
            return SaplUnitTestFixtureFactory.create(singleDocument.getIdentifier());
        }
        return SaplUnitTestFixtureFactory.createFromInputString(
                customUnitTestPolicyResolver.resolvePolicyByIdentifier(singleDocument.getIdentifier()));
    }

}
