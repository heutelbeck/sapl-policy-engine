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

package io.sapl.test.unit;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.utils.DocumentHelper;

class SaplUnitTestFixtureTests {

    @Test
    void test_invalidSaplDocumentName1() {
        final var fixture = new SaplUnitTestFixture("");
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
    }

    @Test
    void test_invalidSaplDocumentName2() {
        final var fixture = new SaplUnitTestFixture("");
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
    }

    @Test
    void test_invalidSaplDocumentName3() {
        final var fixture = new SaplUnitTestFixture(null);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
    }

    @Test
    void test_invalidSaplDocumentName4() {
        final var fixture = new SaplUnitTestFixture(null);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
    }

    @Test
    void test_invalidSaplInputString1() {
        final var fixture = new SaplUnitTestFixture("", false);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
    }

    @Test
    void test_invalidSaplInputString2() {
        final var fixture = new SaplUnitTestFixture("", false);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
    }

    @Test
    void test_invalidSaplInputString3() {
        final var fixture = new SaplUnitTestFixture(null, false);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
    }

    @Test
    void test_invalidSaplInputString4() {
        final var fixture = new SaplUnitTestFixture(null, false);
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
    }

    @Test
    void test_documentHelperErrorThrows1() {
        try (MockedStatic<DocumentHelper> mockedDocumentHelper = Mockito.mockStatic(DocumentHelper.class)) {
            mockedDocumentHelper.when(() -> DocumentHelper.readSaplDocument(eq("foo.sapl"), any()))
                    .thenThrow(new RuntimeException());
            SaplTestFixture fixture = new SaplUnitTestFixture("foo.sapl");
            assertThatExceptionOfType(RuntimeException.class).isThrownBy(fixture::constructTestCase);
        }
    }

    @Test
    void test_documentHelperErrorThrows2() {
        try (MockedStatic<DocumentHelper> mockedDocumentHelper = Mockito.mockStatic(DocumentHelper.class)) {
            mockedDocumentHelper.when(() -> DocumentHelper.readSaplDocument(eq("foo.sapl"), any()))
                    .thenThrow(new RuntimeException());
            SaplTestFixture fixture = new SaplUnitTestFixture("foo.sapl");
            assertThatExceptionOfType(RuntimeException.class).isThrownBy(fixture::constructTestCaseWithMocks);
        }
    }

    @Test
    void test_documentHelperErrorThrowsForInputString1() {
        try (MockedStatic<DocumentHelper> mockedDocumentHelper = Mockito.mockStatic(DocumentHelper.class)) {
            mockedDocumentHelper.when(() -> DocumentHelper.readSaplDocumentFromInputString(eq("foo"), any()))
                    .thenThrow(new RuntimeException());
            SaplTestFixture fixture = new SaplUnitTestFixture("foo", false);
            assertThatExceptionOfType(RuntimeException.class).isThrownBy(fixture::constructTestCase);
        }
    }

    @Test
    void test_documentHelperErrorThrowsForInputString2() {
        try (MockedStatic<DocumentHelper> mockedDocumentHelper = Mockito.mockStatic(DocumentHelper.class)) {
            mockedDocumentHelper.when(() -> DocumentHelper.readSaplDocumentFromInputString(eq("foo"), any()))
                    .thenThrow(new RuntimeException());
            SaplTestFixture fixture = new SaplUnitTestFixture("foo", false);
            assertThatExceptionOfType(RuntimeException.class).isThrownBy(fixture::constructTestCaseWithMocks);
        }
    }
}
