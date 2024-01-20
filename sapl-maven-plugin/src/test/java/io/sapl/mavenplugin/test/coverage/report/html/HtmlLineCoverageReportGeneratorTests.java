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
package io.sapl.mavenplugin.test.coverage.report.html;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.TestFileHelper;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentLineCoverageInformation;

class HtmlLineCoverageReportGeneratorTests {

    private Path base;

    private static final float POLICY_SET_HIT_RATIO       = 100;
    private static final float POLICY_HIT_RATIO           = 66.6f;
    private static final float POLICY_CONDITION_HIT_RATIO = 43.9f;

    private HtmlLineCoverageReportGenerator generator;

    private Collection<SaplDocumentCoverageInformation> documents;

    @BeforeEach
    void setup() {
        base = Paths.get("target/sapl-coverage/html");
        TestFileHelper.deleteDirectory(base.toFile());

        var document = new SaplDocumentCoverageInformation(Paths.get("target/classes/policies/policy_1.sapl"), 12);
        document.markLine(1, LineCoveredValue.IRRELEVANT, 0, 0);
        document.markLine(2, LineCoveredValue.IRRELEVANT, 0, 0);
        document.markLine(3, LineCoveredValue.FULLY, 1, 1);
        document.markLine(4, LineCoveredValue.FULLY, 1, 1);
        document.markLine(5, LineCoveredValue.IRRELEVANT, 0, 0);
        document.markLine(6, LineCoveredValue.FULLY, 1, 1);
        document.markLine(7, LineCoveredValue.IRRELEVANT, 0, 0);
        document.markLine(8, LineCoveredValue.FULLY, 1, 1);
        document.markLine(9, LineCoveredValue.IRRELEVANT, 0, 0);
        document.markLine(10, LineCoveredValue.PARTLY, 1, 2);
        document.markLine(11, LineCoveredValue.NEVER, 0, 2);
        document.markLine(12, LineCoveredValue.NEVER, 0, 2);
        documents = List.of(document);

        generator = new HtmlLineCoverageReportGenerator();
    }

    @AfterEach
    void cleanUp() {
        TestFileHelper.deleteDirectory(base.toFile());
    }

    @Test
    void test() throws MojoExecutionException {

        generator.generateHtmlReport(documents, Paths.get("target/sapl-coverage"), POLICY_SET_HIT_RATIO,
                POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO);

        assertEquals(Boolean.TRUE, base.resolve("assets/images/favicon.png").toFile().exists());
        assertEquals(Boolean.TRUE, base.resolve("assets/images/logo-header.png").toFile().exists());
        assertEquals(Boolean.TRUE, base.resolve("assets/lib/css/main.css").toFile().exists());
        assertEquals(Boolean.TRUE, base.resolve("policies/policy_1.sapl.html").toFile().exists());
        assertEquals(Boolean.TRUE, base.resolve("report.html").toFile().exists());
    }

    @Test
    void whenUnknownLineCoveredValue_testExceptionsAreThrown() {

        try (MockedStatic<LineCoveredValue> x = mockStatic(LineCoveredValue.class)) {
            LineCoveredValue badApple = mock(LineCoveredValue.class);
            when(badApple.ordinal()).thenReturn(4);
            when(LineCoveredValue.values()).thenReturn(new LineCoveredValue[] { LineCoveredValue.FULLY,
                    LineCoveredValue.PARTLY, LineCoveredValue.NEVER, LineCoveredValue.IRRELEVANT, badApple });

            try (MockedConstruction<SaplDocumentLineCoverageInformation> mocked = Mockito.mockConstruction(
                    SaplDocumentLineCoverageInformation.class,
                    (mock, context) -> when(mock.getCoveredValue()).thenReturn(badApple))) {
                base = Paths.get("target/sapl-coverage/html");
                TestFileHelper.deleteDirectory(base.toFile());

                var document = new SaplDocumentCoverageInformation(Paths.get("target/classes/policies/policy_1.sapl"),
                        12);
                document.markLine(1, LineCoveredValue.IRRELEVANT, 0, 0);
                documents = List.of(document);
                generator = new HtmlLineCoverageReportGenerator();
                var path = Paths.get("target/sapl-coverage");
                assertThrows(SaplTestException.class, () -> generator.generateHtmlReport(documents, path,
                        POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO));
            }
        }
    }

    @Test
    void test_readFileFromClasspath_IOException() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.writeString(Mockito.any(), Mockito.any())).thenThrow(IOException.class);
            assertThrows(MojoExecutionException.class,
                    () -> generator.generateHtmlReport(documents, Paths.get("target/sapl-coverage"),
                            POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO));
        }
    }

    @Test
    void test_fileWithInvalidPath() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.writeString(Mockito.any(), Mockito.any())).thenReturn(Path.of(""));
            mockedFiles.when(() -> Files.readAllLines(Mockito.any())).thenReturn(List.of(""));
            mockedFiles.when(() -> Files.copy(Mockito.any(), Mockito.any())).thenReturn(0L);

            Path mockedPath = Mockito.mock(Path.class);
            Mockito.when(mockedPath.getFileName()).thenReturn(null);
            var document = new SaplDocumentCoverageInformation(mockedPath, 1);
            documents = List.of(document);
            assertDoesNotThrow(() -> generator.generateHtmlReport(documents, Paths.get("target/sapl-coverage"),
                    POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO));
        }
    }

}
