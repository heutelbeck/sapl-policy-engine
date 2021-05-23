package io.sapl.mavenplugin.test.coverage;

import java.nio.file.Path;
import java.util.Collection;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.sapl.mavenplugin.test.coverage.helper.CoverageAPIHelper;
import io.sapl.mavenplugin.test.coverage.helper.CoverageRatioCalculator;
import io.sapl.mavenplugin.test.coverage.helper.CoverageTargetHelper;
import io.sapl.mavenplugin.test.coverage.helper.SaplDocumentReader;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.mavenplugin.test.coverage.report.GenericCoverageReporter;
import io.sapl.mavenplugin.test.coverage.report.html.HtmlLineCoverageReportGenerator;
import io.sapl.mavenplugin.test.coverage.report.sonar.SonarLineCoverageReportGenerator;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

@Mojo(name = "report-coverage-information", defaultPhase = LifecyclePhase.VERIFY)
public class ReportCoverageInformationMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "true")
	private boolean coverageEnabled;

	@Parameter(defaultValue = "")
	private String outputDir;

	@Parameter(defaultValue = "policies")
	private String policyPath;

	@Parameter(defaultValue = "0")
	private float policySetHitRatio;

	@Parameter(defaultValue = "0")
	private float policyHitRatio;

	@Parameter(defaultValue = "0")
	private float policyConditionHitRatio;

	@Parameter(defaultValue = "false")
	private boolean enableSonarReport;

	@Parameter(defaultValue = "true")
	private boolean enableHtmlReport;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (!this.coverageEnabled) {
			getLog().info("Policy Coverage Collection is disabled");
			return;
		}

		// read available targets & hits
		Collection<SaplDocument> documents = readSaplDocuments();
		CoverageTargets targets = readAvailableTargets(documents);
		CoverageTargets hits = readHits();
		
		var actualPolicySetHitRatio = CoverageRatioCalculator.calculatePolicySetHitRatio(targets.getPolicySets(), hits.getPolicySets());
		var actualPolicyHitRatio = CoverageRatioCalculator.calculatePolicyHitRatio(targets.getPolicys(), hits.getPolicys());
		var actualPolicyConditionHitRatio = CoverageRatioCalculator.calculatePolicyConditionHitRatio(targets.getPolicyConditions(), hits.getPolicyConditions());

		getLog().info("");
		getLog().info("");
		getLog().info("Measured SAPL coverage information:");
		getLog().info("");

		boolean isPolicySetRatioFulfilled = checkPolicySetRatio(targets.getPolicySets(), actualPolicySetHitRatio);

		boolean isPolicyRatioFulfilled = checkPolicyRatio(targets.getPolicys(), actualPolicyHitRatio);

		boolean isPolicyConditionRatioFulfilled = checkPolicyConditionRatio(targets.getPolicyConditions(), actualPolicyConditionHitRatio);

		getLog().info("");
		getLog().info("");
		
		// generate coverage report
		if (enableSonarReport || enableHtmlReport) {
			// create generic report information
			GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
			var genericDocumentCoverage = reporter.calcDocumentCoverage();

			if (enableSonarReport) {
				// write to sonar specific report format
				SonarLineCoverageReportGenerator sonarReporter = new SonarLineCoverageReportGenerator(
						genericDocumentCoverage, getLog(),
						PathHelper.resolveBaseDir(outputDir, project.getBuild().getDirectory(), getLog()),
						this.policyPath, this.project.getBasedir());
				sonarReporter.generateSonarLineCoverageReport();
			}

			if (enableHtmlReport) {
				// create HTML report
				HtmlLineCoverageReportGenerator htmlReporter = new HtmlLineCoverageReportGenerator(
						genericDocumentCoverage, getLog(),
						PathHelper.resolveBaseDir(outputDir, project.getBuild().getDirectory(), getLog()),
						actualPolicySetHitRatio, actualPolicyHitRatio, actualPolicyConditionHitRatio);
				Path indexHtml = htmlReporter.generateHtmlReport();
				getLog().info("Open this file in a Browser to view the HTML coverage report: ");
				getLog().info(indexHtml.toUri().toString());
			}
		}

		getLog().info("");
		getLog().info("");
		// break lifecycle if ratio is not fulfilled
		if (isPolicySetRatioFulfilled && isPolicyRatioFulfilled && isPolicyConditionRatioFulfilled) {
			getLog().info("All coverage criteria passed");
			getLog().info("");
			getLog().info("");
		} else {
			throw new MojoFailureException(
					"One or more SAPL Coverage Ratios aren't fulfilled! Find further information above.");
		}
	}

	private Collection<SaplDocument> readSaplDocuments() throws MojoExecutionException {
		try {
			getLog().debug("Loading coverage targets");
			SaplDocumentReader reader = new SaplDocumentReader(getLog(), project);
			getLog().debug("Successful read coverage targets");
			return reader.retrievePolicyDocuments(this.policyPath);
		} catch (Exception e) {
			getLog().error("Error reading coverage targets: " + e.getMessage(), e);
		}
		throw new MojoExecutionException("Error reading coverage targets");
	}

	private CoverageTargets readAvailableTargets(Collection<SaplDocument> documents) {
		CoverageTargetHelper helper = new CoverageTargetHelper();
		return helper.getCoverageTargets(documents);
	}

	private CoverageTargets readHits() throws MojoExecutionException {
		try {
			getLog().debug("Loading coverage hits");
			CoverageAPIHelper helper = new CoverageAPIHelper(
					PathHelper.resolveBaseDir(outputDir, project.getBuild().getDirectory(), getLog()));
			getLog().debug("Successful read coverage hits");
			return helper.readHits();
		} catch (Exception e) {
			getLog().error("Error reading coverage hits: " + e.getMessage(), e);
		}
		throw new MojoExecutionException("Error reading coverage hits");
	}

	private boolean checkPolicySetRatio(Collection<PolicySetHit> targets, float ratio) {
		if (targets.isEmpty()) {
			getLog().info("There are no PolicySets to hit");
		} else {
			
			getLog().info("PolicySet Hit Ratio is: " + ratio);
		}

		if (ratio < policySetHitRatio) {
			getLog().error("PolicySet Hit Ratio not fulfilled - Expected greater or equal " + policySetHitRatio
					+ " but got " + ratio);
			return false;
		} else {
			return true;
		}
	}

	private boolean checkPolicyRatio(Collection<PolicyHit> targets, float ratio) {
		if (targets.isEmpty()) {
			getLog().info("There are no Policys to hit");
		} else {
			getLog().info("Policy Hit Ratio is: " + ratio);
		}
		
		if (ratio < policyHitRatio) {
			getLog().error("Policy Hit Ratio not fulfilled - Expected greater or equal " + policyHitRatio + " but got "
					+ ratio);
			return false;
		} else {
			return true;
		}
	}

	private boolean checkPolicyConditionRatio(Collection<PolicyConditionHit> targets, float ratio) {
		if (targets.isEmpty()) {
			getLog().info("There are no PolicyConditions to hit");
		} else {
			getLog().info("PolicyCondition Hit Ratio is: " + ratio);
		}

		if (ratio < policyConditionHitRatio) {
			getLog().error("PolicyCondition Hit Ratio not fulfilled - Expected greater or equal "
					+ policyConditionHitRatio + " but got " + ratio);
			return false;
		} else {
			return true;
		}
	}
}