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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.report.SampleCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.html.WebDependencyFactory.WebDependency;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentLineCoverageInformation;

class HtmlLineCoverageReportGeneratorTests {

    private static final float                           POLICY_SET_HIT_RATIO       = 100;
    private static final float                           POLICY_HIT_RATIO           = 66.6f;
    private static final float                           POLICY_CONDITION_HIT_RATIO = 43.9f;
    private static final HtmlLineCoverageReportGenerator GENERATOR                  = new HtmlLineCoverageReportGenerator();;

    @Test
    void test(@TempDir Path tempDir) throws MojoExecutionException {
        GENERATOR.generateHtmlReport(SampleCoverageInformation.documents(), tempDir, POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO,
                POLICY_CONDITION_HIT_RATIO);
        assertEquals(Boolean.TRUE, tempDir.resolve("html/assets/images/favicon.png").toFile().exists());
        assertEquals(Boolean.TRUE, tempDir.resolve("html/assets/images/logo-header.png").toFile().exists());
        assertEquals(Boolean.TRUE, tempDir.resolve("html/assets/lib/css/main.css").toFile().exists());
        assertEquals(Boolean.TRUE, tempDir.resolve("html/policies/policy_1.sapl.html").toFile().exists());
        assertEquals(Boolean.TRUE, tempDir.resolve("html/report.html").toFile().exists());
    }

    @Test
    void whenUnknownLineCoveredValue_testExceptionsAreThrown(@TempDir Path tempDir) {
        try (MockedStatic<LineCoveredValue> x = mockStatic(LineCoveredValue.class)) {
            LineCoveredValue badApple = mock(LineCoveredValue.class);
            when(badApple.ordinal()).thenReturn(4);
            when(LineCoveredValue.values()).thenReturn(new LineCoveredValue[] { LineCoveredValue.FULLY,
                    LineCoveredValue.PARTLY, LineCoveredValue.NEVER, LineCoveredValue.IRRELEVANT, badApple });

            try (MockedConstruction<SaplDocumentLineCoverageInformation> mocked = Mockito.mockConstruction(
                    SaplDocumentLineCoverageInformation.class,
                    (mock, context) -> when(mock.getCoveredValue()).thenReturn(badApple))) {
                var document = new SaplDocumentCoverageInformation(
                        Paths.get("src/test/resources/policies/policy_1.sapl"), 12);
                document.markLine(1, LineCoveredValue.IRRELEVANT, 0, 0);
                var documents = List.of(document);
                assertThrows(SaplTestException.class, () -> GENERATOR.generateHtmlReport(documents, tempDir,
                        POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO));
            }
        }
    }

    @Test
    void test_readFileFromClasspath_IOException(@TempDir Path tempDir) {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.writeString(Mockito.any(), Mockito.any())).thenThrow(IOException.class);
            assertThrows(MojoExecutionException.class, () -> GENERATOR.generateHtmlReport(SampleCoverageInformation.documents(), tempDir,
                    POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO));
        }
    }

    @Test
    void test_fileWithInvalidPath(@TempDir Path tempDir) {
        try (MockedStatic<WebDependencyFactory> mockedDependencyFactory = Mockito
                .mockStatic(WebDependencyFactory.class)) {
            var nonExistingFiles = List
                    .of(new WebDependency("I do not exist", "I do not exist", "I do not exist", "I do not exist"));
            mockedDependencyFactory.when(() -> WebDependencyFactory.getWebDependencies()).thenReturn(nonExistingFiles);
            assertThrows(MojoExecutionException.class, () -> GENERATOR.generateHtmlReport(SampleCoverageInformation.documents(), tempDir,
                    POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO));
        }
    }

}
