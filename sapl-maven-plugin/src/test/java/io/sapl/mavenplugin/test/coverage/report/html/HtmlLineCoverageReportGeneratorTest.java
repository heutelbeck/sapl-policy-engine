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
	
	@BeforeEach
	void setup() {
		base = Paths.get("target/sapl-coverage/html");
		TestFileHelper.deleteDirectory(base.toFile());
	}

	
	
	@Test
	public void test() {
		var policySetHitRatio = 100;
		var policyHitRatio = 66.6f;
		var policyConditionHitRatio = 43.9f;
		var document = new SaplDocumentCoverageInformation(Paths.get("target/classes/policies/policy_1.sapl"), 12);
		document.markLine(1, LineCoveredValue.IRRELEVANT, 0, 0);
		document.markLine(2, LineCoveredValue.IRRELEVANT, 0, 0);
		document.markLine(3, LineCoveredValue.FULLY, 1, 1);
		document.markLine(4, LineCoveredValue.FULLY, 1, 1);
		document.markLine(5, LineCoveredValue.IRRELEVANT, 0, 0);
		document.markLine(6, LineCoveredValue.FULLY, 1, 1);
		document.markLine(7, LineCoveredValue.IRRELEVANT, 0, 0);
		document.markLine(8, LineCoveredValue.FULLY, 1,1 );
		document.markLine(9, LineCoveredValue.IRRELEVANT, 0, 0);
		document.markLine(10, LineCoveredValue.PARTLY, 1, 2);
		document.markLine(11, LineCoveredValue.NEVER, 0, 2);
		document.markLine(12, LineCoveredValue.NEVER, 0, 2);
		Collection<SaplDocumentCoverageInformation> documents = List.of(document);
		HtmlLineCoverageReportGenerator generator = new HtmlLineCoverageReportGenerator();
		
		
		generator.generateHtmlReport(documents, new SilentLog(), Paths.get("target/sapl-coverage"),
				policySetHitRatio, policyHitRatio, policyConditionHitRatio);
		
		assertEquals(true, base.resolve("assets/favicon.png").toFile().exists());
		assertEquals(true, base.resolve("assets/logo-header.png").toFile().exists());
		assertEquals(true, base.resolve("assets/main.css").toFile().exists());
		assertEquals(true, base.resolve("policies/policy_1.sapl.html").toFile().exists());
		assertEquals(true, base.resolve("index.html").toFile().exists());
	}
}
