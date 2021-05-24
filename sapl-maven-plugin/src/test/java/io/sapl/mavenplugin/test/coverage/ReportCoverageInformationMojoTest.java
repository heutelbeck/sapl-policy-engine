package io.sapl.mavenplugin.test.coverage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.times;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.mavenplugin.test.coverage.helper.CoverageAPIHelper;
import io.sapl.mavenplugin.test.coverage.helper.CoverageRatioCalculator;
import io.sapl.mavenplugin.test.coverage.helper.CoverageTargetHelper;
import io.sapl.mavenplugin.test.coverage.helper.SaplDocumentReader;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.report.GenericCoverageReporter;
import io.sapl.mavenplugin.test.coverage.report.html.HtmlLineCoverageReportGenerator;
import io.sapl.mavenplugin.test.coverage.report.sonar.SonarLineCoverageReportGenerator;

class ReportCoverageInformationMojoTest extends AbstractMojoTestCase {

	private SaplDocumentReader saplDocumentReader;
	private CoverageTargetHelper coverageTargetHelper;
	private CoverageAPIHelper coverageAPIHelper;
	private CoverageRatioCalculator ratioCalculator;
	private GenericCoverageReporter reporter;
	private SonarLineCoverageReportGenerator sonarReporter;
	private HtmlLineCoverageReportGenerator htmlReporter;
	private Log log;

	@BeforeEach
	void setup() throws Exception {
		super.setUp();
		saplDocumentReader = Mockito.mock(SaplDocumentReader.class);
		coverageTargetHelper = Mockito.mock(CoverageTargetHelper.class);
		coverageAPIHelper = Mockito.mock(CoverageAPIHelper.class);
		ratioCalculator = Mockito.mock(CoverageRatioCalculator.class);
		reporter = Mockito.mock(GenericCoverageReporter.class);
		sonarReporter = Mockito.mock(SonarLineCoverageReportGenerator.class);
		htmlReporter = Mockito.mock(HtmlLineCoverageReportGenerator.class);
		log = Mockito.mock(Log.class);
		
		getContainer().addComponent(saplDocumentReader, SaplDocumentReader.class, "");
		getContainer().addComponent(coverageTargetHelper, CoverageTargetHelper.class, "");
		getContainer().addComponent(coverageAPIHelper, CoverageAPIHelper.class, "");
		getContainer().addComponent(ratioCalculator, CoverageRatioCalculator.class, "");
		getContainer().addComponent(reporter, GenericCoverageReporter.class, "");
		getContainer().addComponent(sonarReporter, SonarLineCoverageReportGenerator.class, "");
		getContainer().addComponent(htmlReporter, HtmlLineCoverageReportGenerator.class, "");
		

	}

	@Test
	void test_disableCoverage() throws Exception {

		Path pom = Paths.get("src", "test", "resources", "pom", "pom_coverageDisabled.xml");
		var mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
		mojo.setLog(this.log);
		
		mojo.execute();
		Mockito.verify(log).info("Policy Coverage Collection is disabled");
		//Mockito.verify(saplDocumentReader).retrievePolicyDocuments(Mockito.any(), Mockito.any(), Mockito.any());

	}
	
	@Test
	void test_happyPath() throws Exception {

		Mockito.when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
		CoverageTargets targets = new CoverageTargets(List.of(), List.of(), List.of());
		Mockito.when(this.coverageTargetHelper.getCoverageTargets(any())).thenReturn(targets);
		Mockito.when(this.coverageAPIHelper.readHits(any())).thenReturn(targets);
		Mockito.when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f,100f,100f);
		Mockito.when(this.htmlReporter.generateHtmlReport(any(), any(), any(), anyFloat(), anyFloat(), anyFloat())).thenReturn(Paths.get("test", "index.html"));
		
		Path pom = Paths.get("src", "test", "resources", "pom", "pom.xml");
		var mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
		mojo.setLog(this.log);
		
		mojo.execute();

		Mockito.verify(saplDocumentReader, times(1)).retrievePolicyDocuments(Mockito.any(), Mockito.any(), Mockito.any());
		Mockito.verify(this.htmlReporter, times(1)).generateHtmlReport(any(),  any(),  any(),  anyFloat(), anyFloat(), anyFloat());
		Mockito.verify(this.sonarReporter, times(1)).generateSonarLineCoverageReport(any(),  any(),  any(),  any(),  any());
		
		Mockito.verify(log).info("All coverage criteria passed");
	}
	
	
	@Test
	void test_criteriaNotFulfilled() throws Exception {

		Mockito.when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
		CoverageTargets targets = new CoverageTargets(List.of(), List.of(), List.of());
		Mockito.when(this.coverageTargetHelper.getCoverageTargets(any())).thenReturn(targets);
		Mockito.when(this.coverageAPIHelper.readHits(any())).thenReturn(targets);
		Mockito.when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f,100f,50f);
		Mockito.when(this.htmlReporter.generateHtmlReport(any(), any(), any(), anyFloat(), anyFloat(), anyFloat())).thenReturn(Paths.get("test", "index.html"));
			
		Path pom = Paths.get("src", "test", "resources", "pom", "pom.xml");
		var mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
		mojo.setLog(this.log);
		
		assertThrows(MojoFailureException.class, () -> mojo.execute());
		
		Mockito.verify(saplDocumentReader, times(1)).retrievePolicyDocuments(Mockito.any(), Mockito.any(), Mockito.any());
		Mockito.verify(this.htmlReporter, times(1)).generateHtmlReport(any(),  any(),  any(),  anyFloat(), anyFloat(), anyFloat());
		Mockito.verify(this.sonarReporter, times(1)).generateSonarLineCoverageReport(any(),  any(),  any(),  any(),  any());
		
		Mockito.verify(log).error("PolicyCondition Hit Ratio not fulfilled - Expected greater or equal 80.0 but got 50.0");
	}
}
