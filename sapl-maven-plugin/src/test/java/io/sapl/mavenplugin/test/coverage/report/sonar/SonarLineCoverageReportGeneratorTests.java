/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.SilentLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.mavenplugin.test.coverage.TestFileHelper;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.sonar.model.Coverage;
import io.sapl.mavenplugin.test.coverage.report.sonar.model.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

class SonarLineCoverageReportGeneratorTests {

	private SonarLineCoverageReportGenerator generator;

	private Collection<SaplDocumentCoverageInformation> documents;

	private final Path base = Paths.get("target/sapl-coverage/sonar");

	@BeforeEach
	void setup() {
		TestFileHelper.deleteDirectory(base.toFile());

		this.generator = new SonarLineCoverageReportGenerator();
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
		document.markLine(11, LineCoveredValue.NEVER, 1, 2);
		document.markLine(12, LineCoveredValue.NEVER, 1, 2);
		documents = List.of(document);
	}

	@Test
	void test() throws IOException, MojoExecutionException {

		generator.generateSonarLineCoverageReport(documents, new SilentLog(), Paths.get("target/sapl-coverage"),
				"policies", Paths.get(".").toFile());

		List<String> lines = Files.readAllLines(base.resolve("sonar-generic-coverage.xml"));
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
	void test_IOException() {
		try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
			mockedFiles.when(() -> Files.createFile(Mockito.any())).thenThrow(IOException.class);
			assertThrows(MojoExecutionException.class, () -> generator.generateSonarLineCoverageReport(documents,
					new SilentLog(), Paths.get("target/sapl-coverage"), "policies", Paths.get(".").toFile()));
		}
	}

	@Test
	void test_JAXBException() {
		try (MockedStatic<JAXBContext> jaxbContext = Mockito.mockStatic(JAXBContext.class)) {
			jaxbContext.when(() -> JAXBContext.newInstance(Coverage.class, ObjectFactory.class))
					.thenThrow(JAXBException.class);
			assertThrows(MojoExecutionException.class, () -> generator.generateSonarLineCoverageReport(documents,
					new SilentLog(), Paths.get("target/sapl-coverage"), "policies", Paths.get(".").toFile()));
		}
	}

}
