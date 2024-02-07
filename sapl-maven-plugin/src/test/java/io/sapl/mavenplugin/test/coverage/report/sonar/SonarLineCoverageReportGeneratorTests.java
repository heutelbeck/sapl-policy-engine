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
package io.sapl.mavenplugin.test.coverage.report.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.SilentLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.mavenplugin.test.coverage.PathHelper;
import io.sapl.mavenplugin.test.coverage.report.SampleCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.sonar.model.Coverage;
import io.sapl.mavenplugin.test.coverage.report.sonar.model.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

class SonarLineCoverageReportGeneratorTests {

    private static final SonarLineCoverageReportGenerator GENERATOR = new SonarLineCoverageReportGenerator();

    @Test
    void test(@TempDir Path tempDir) throws IOException, MojoExecutionException {

        GENERATOR.generateSonarLineCoverageReport(SampleCoverageInformation.documents(), new SilentLog(), tempDir,
                "policies", Paths.get(".").toFile());

        List<String> lines = Files.readAllLines(tempDir.resolve("sonar/sonar-generic-coverage.xml"));
        assertEquals(12, lines.size());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>", lines.get(0));
        assertEquals("<coverage version=\"1\">", lines.get(1));
        assertEquals("    <file path=\"." + File.separator + "src" + File.separator + "main" + File.separator
                + "resources" + File.separator + "policies" + File.separator + "policy_1.sapl\">", lines.get(2));
        assertEquals("        <lineToCover lineNumber=\"3\" covered=\"true\"/>", lines.get(3));
        assertEquals("        <lineToCover lineNumber=\"4\" covered=\"true\"/>", lines.get(4));
        assertEquals("        <lineToCover lineNumber=\"6\" covered=\"true\"/>", lines.get(5));
        assertEquals("        <lineToCover lineNumber=\"8\" covered=\"true\"/>", lines.get(6));
        assertEquals(
                "        <lineToCover lineNumber=\"10\" covered=\"true\" branchesToCover=\"2\" coveredBranches=\"1\"/>",
                lines.get(7));
        assertEquals("        <lineToCover lineNumber=\"11\" covered=\"false\"/>", lines.get(8));
        assertEquals("        <lineToCover lineNumber=\"12\" covered=\"false\"/>", lines.get(9));
        assertEquals("    </file>", lines.get(10));
        assertEquals("</coverage>", lines.get(11));

    }

    @Test
    void test_IOException(@TempDir Path tempDir) {
        try (MockedStatic<PathHelper> mockedHelper = Mockito.mockStatic(PathHelper.class)) {
            mockedHelper.when(() -> PathHelper.createFile(any())).thenThrow(IOException.class);
            assertThrows(MojoExecutionException.class,
                    () -> GENERATOR.generateSonarLineCoverageReport(SampleCoverageInformation.documents(),
                            new SilentLog(), tempDir, "policies", Paths.get(".").toFile()));
        }
    }

    @Test
    void test_JAXBException(@TempDir Path tempDir) {
        try (MockedStatic<JAXBContext> jaxbContext = Mockito.mockStatic(JAXBContext.class)) {
            jaxbContext.when(() -> JAXBContext.newInstance(Coverage.class, ObjectFactory.class))
                    .thenThrow(JAXBException.class);
            assertThrows(MojoExecutionException.class,
                    () -> GENERATOR.generateSonarLineCoverageReport(SampleCoverageInformation.documents(),
                            new SilentLog(), tempDir, "policies", Paths.get(".").toFile()));
        }
    }

}
