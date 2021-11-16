/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.TestFileHelper;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;

public class HtmlLineCoverageReportGeneratorTest {

	private Path base;
	private float policySetHitRatio = 100;
	private float policyHitRatio = 66.6f;
	private float policyConditionHitRatio = 43.9f;
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

	@Test
	public void test() {

		generator.generateHtmlReport(documents, new SilentLog(), Paths.get("target/sapl-coverage"), policySetHitRatio,
				policyHitRatio, policyConditionHitRatio);

		assertEquals(true, base.resolve("assets/favicon.png").toFile().exists());
		assertEquals(true, base.resolve("assets/logo-header.png").toFile().exists());
		assertEquals(true, base.resolve("assets/main.css").toFile().exists());
		assertEquals(true, base.resolve("policies/policy_1.sapl.html").toFile().exists());
		assertEquals(true, base.resolve("index.html").toFile().exists());
	}
}
