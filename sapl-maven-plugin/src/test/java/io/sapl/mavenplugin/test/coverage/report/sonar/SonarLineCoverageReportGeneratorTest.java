package io.sapl.mavenplugin.test.coverage.report.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.TestFileHelper;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;

public class SonarLineCoverageReportGeneratorTest {

	@Test
	public void test() throws IOException {
		Path base = Paths.get("target/sapl-coverage/sonar");
		TestFileHelper.deleteDirectory(base.toFile());

		var document = new SaplDocumentCoverageInformation(Paths.get("target/classes/policies/policy_1.sapl"), 12);
		document.markLine(1, LineCoveredValue.UNINTERESTING, 0, 0);
		document.markLine(2, LineCoveredValue.UNINTERESTING, 0, 0);
		document.markLine(3, LineCoveredValue.FULLY, 1, 1);
		document.markLine(4, LineCoveredValue.FULLY, 1, 1);
		document.markLine(5, LineCoveredValue.UNINTERESTING, 0, 0);
		document.markLine(6, LineCoveredValue.FULLY, 1, 1);
		document.markLine(7, LineCoveredValue.UNINTERESTING, 0, 0);
		document.markLine(8, LineCoveredValue.FULLY, 1,1 );
		document.markLine(9, LineCoveredValue.UNINTERESTING, 0, 0);
		document.markLine(10, LineCoveredValue.PARTLY, 1, 2);
		document.markLine(11, LineCoveredValue.NEVER, 1, 2);
		document.markLine(12, LineCoveredValue.NEVER, 1, 2);
		Collection<SaplDocumentCoverageInformation> documents = List.of(document);
		SonarLineCoverageReportGenerator generator = new SonarLineCoverageReportGenerator();

		generator.generateSonarLineCoverageReport(documents, new SilentLog(),
				Paths.get("target/sapl-coverage"), "policies", Paths.get(".").toFile());

		List<String> lines = Files.readAllLines(base.resolve("sonar-generic-coverage.xml"));
		assertEquals(12, lines.size());
		assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>", lines.get(0));
		assertEquals("<coverage version=\"1\">", lines.get(1));
		assertEquals("    <file path=\"." + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "policies" + File.separator + "policy_1.sapl\">", lines.get(2));
		assertEquals("        <lineToCover lineNumber=\"3\" covered=\"true\"/>", lines.get(3));
		assertEquals("        <lineToCover lineNumber=\"4\" covered=\"true\"/>", lines.get(4));
		assertEquals("        <lineToCover lineNumber=\"6\" covered=\"true\"/>", lines.get(5));
		assertEquals("        <lineToCover lineNumber=\"8\" covered=\"true\"/>", lines.get(6));
		assertEquals("        <lineToCover lineNumber=\"10\" covered=\"true\" branchesToCover=\"2\" coveredBranches=\"1\"/>", lines.get(7));
		assertEquals("        <lineToCover lineNumber=\"11\" covered=\"false\"/>", lines.get(8));
		assertEquals("        <lineToCover lineNumber=\"12\" covered=\"false\"/>", lines.get(9));
		assertEquals("    </file>", lines.get(10));
		assertEquals("</coverage>", lines.get(11));

	}
}
