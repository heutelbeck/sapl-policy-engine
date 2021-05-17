package io.sapl.mavenplugin.test.coverage.report.sonar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.TestFileHelper;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;

public class SonarLineCoverageReportGeneratorTest {

	@Test
	public void test() throws IOException {
		Path base = Paths.get("target/sapl-coverage/sonar");
		TestFileHelper.deleteDirectory(base.toFile());

		var document = new SaplDocumentCoverageInformation(Paths.get("target/classes/policies/policy_1.sapl"), 11);
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
		document.markLine(11, LineCoveredValue.PARTLY, 1, 2);
		Collection<SaplDocumentCoverageInformation> documents = List.of(document);
		SonarLineCoverageReportGenerator generator = new SonarLineCoverageReportGenerator(documents, new SilentLog(),
				Paths.get("target/sapl-coverage"), "policies", Paths.get(".").toFile());

		generator.generateSonarLineCoverageReport();

		List<String> lines = Files.readAllLines(base.resolve("sonar-generic-coverage.xml"));
		Assertions.assertThat(lines.size()).isEqualTo(11);
		Assertions.assertThat(lines.get(0)).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		Assertions.assertThat(lines.get(1)).isEqualTo("<coverage version=\"1\">");
		Assertions.assertThat(lines.get(2)).isEqualTo("    <file path=\"." + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "policies" + File.separator + "policy_1.sapl\">");
		Assertions.assertThat(lines.get(3)).isEqualTo("        <lineToCover lineNumber=\"3\" covered=\"true\"/>");
		Assertions.assertThat(lines.get(4)).isEqualTo("        <lineToCover lineNumber=\"4\" covered=\"true\"/>");
		Assertions.assertThat(lines.get(5)).isEqualTo("        <lineToCover lineNumber=\"6\" covered=\"true\"/>");
		Assertions.assertThat(lines.get(6)).isEqualTo("        <lineToCover lineNumber=\"8\" covered=\"true\"/>");
		Assertions.assertThat(lines.get(7)).isEqualTo("        <lineToCover lineNumber=\"10\" covered=\"true\" branchesToCover=\"2\" coveredBranches=\"1\"/>");
		Assertions.assertThat(lines.get(8)).isEqualTo("        <lineToCover lineNumber=\"11\" covered=\"true\" branchesToCover=\"2\" coveredBranches=\"1\"/>");
		Assertions.assertThat(lines.get(9)).isEqualTo("    </file>");
		Assertions.assertThat(lines.get(10)).isEqualTo("</coverage>");

	}
}
