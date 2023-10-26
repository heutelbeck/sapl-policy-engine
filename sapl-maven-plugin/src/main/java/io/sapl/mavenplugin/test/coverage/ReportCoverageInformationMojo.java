/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;

import javax.inject.Inject;

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

    @Parameter
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

    private final SaplDocumentReader saplDocumentReader;

    private final CoverageTargetHelper coverageTargetHelper;

    private final CoverageAPIHelper coverageAPIHelper;

    private final CoverageRatioCalculator ratioCalculator;

    private final GenericCoverageReporter reporter;

    private final SonarLineCoverageReportGenerator sonarReporter;

    private final HtmlLineCoverageReportGenerator htmlReporter;

    @Inject
    public ReportCoverageInformationMojo(SaplDocumentReader reader, CoverageTargetHelper coverageTargetHelper,
            CoverageAPIHelper coverageAPIHelper, CoverageRatioCalculator calc, GenericCoverageReporter reporter,
            SonarLineCoverageReportGenerator sonarReporter, HtmlLineCoverageReportGenerator htmlReporter) {
        this.saplDocumentReader   = reader;
        this.coverageTargetHelper = coverageTargetHelper;
        this.coverageAPIHelper    = coverageAPIHelper;
        this.ratioCalculator      = calc;
        this.reporter             = reporter;
        this.sonarReporter        = sonarReporter;
        this.htmlReporter         = htmlReporter;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!this.coverageEnabled) {
            getLog().info("Policy Coverage Collection is disabled");
            return;
        }

        Collection<SaplDocument> documents = readSaplDocuments();
        CoverageTargets          targets   = readAvailableTargets(documents);
        CoverageTargets          hits      = readHits();

        var actualPolicySetHitRatio       = this.ratioCalculator.calculateRatio(targets.getPolicySets(),
                hits.getPolicySets());
        var actualPolicyHitRatio          = this.ratioCalculator.calculateRatio(targets.getPolicies(),
                hits.getPolicies());
        var actualPolicyConditionHitRatio = this.ratioCalculator.calculateRatio(targets.getPolicyConditions(),
                hits.getPolicyConditions());

        getLog().info("");
        getLog().info("");
        getLog().info("Measured SAPL coverage information:");
        getLog().info("");

        boolean isPolicySetRatioFulfilled = checkPolicySetRatio(targets.getPolicySets(), actualPolicySetHitRatio);

        boolean isPolicyRatioFulfilled = checkPolicyRatio(targets.getPolicies(), actualPolicyHitRatio);

        boolean isPolicyConditionRatioFulfilled = checkPolicyConditionRatio(targets.getPolicyConditions(),
                actualPolicyConditionHitRatio);

        getLog().info("");
        getLog().info("");

        if (enableSonarReport || enableHtmlReport) {

            var genericDocumentCoverage = reporter.calcDocumentCoverage(documents, hits);

            if (enableSonarReport) {
                sonarReporter.generateSonarLineCoverageReport(genericDocumentCoverage, getLog(),
                        PathHelper.resolveBaseDir(outputDir, project.getBuild().getDirectory(), getLog()),
                        this.policyPath, this.project.getBasedir());
            }

            if (enableHtmlReport) {
                Path indexHtml = htmlReporter.generateHtmlReport(genericDocumentCoverage,
                        PathHelper.resolveBaseDir(outputDir, project.getBuild().getDirectory(), getLog()),
                        actualPolicySetHitRatio, actualPolicyHitRatio, actualPolicyConditionHitRatio);
                getLog().info("Open this file in a Browser to view the HTML coverage report: ");
                getLog().info(indexHtml.toUri().toString());
            }
        }

        getLog().info("");
        getLog().info("");

        breakLifecycleIfRatiosNotFulfilled(isPolicySetRatioFulfilled, isPolicyRatioFulfilled,
                isPolicyConditionRatioFulfilled);
    }

    private void breakLifecycleIfRatiosNotFulfilled(boolean isPolicySetRatioFulfilled, boolean isPolicyRatioFulfilled,
            boolean isPolicyConditionRatioFulfilled) throws MojoFailureException {
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
        getLog().debug("Loading coverage targets");
        var docs = this.saplDocumentReader.retrievePolicyDocuments(getLog(), project, this.policyPath);
        getLog().debug("Successful read coverage targets");
        return docs;
    }

    private CoverageTargets readAvailableTargets(Collection<SaplDocument> documents) {
        return this.coverageTargetHelper.getCoverageTargets(documents);
    }

    private CoverageTargets readHits() {
        getLog().debug("Loading coverage hits");
        var hits = this.coverageAPIHelper
                .readHits(PathHelper.resolveBaseDir(outputDir, project.getBuild().getDirectory(), getLog()));
        getLog().debug("Successful read coverage hits");
        return hits;
    }

    private boolean checkPolicySetRatio(Collection<PolicySetHit> targets, float ratio) {
        if (targets.isEmpty()) {
            getLog().info("There are no PolicySets to hit");
            return true;
        }

        getLog().info("Policy Set Hit Ratio is: " + ratio);

        if (ratio < policySetHitRatio) {
            getLog().error(String.format(Locale.US,
                    "Policy Set Hit Ratio not fulfilled - Expected greater or equal %.2f but got %.2f",
                    policySetHitRatio, ratio));
            return false;
        } else {
            return true;
        }
    }

    private boolean checkPolicyRatio(Collection<PolicyHit> targets, float ratio) {
        if (targets.isEmpty()) {
            getLog().info("There are no Policies to hit");
            return true;
        }

        getLog().info("Policy Hit Ratio is: " + ratio);

        if (ratio < policyHitRatio) {
            getLog().error(String.format(Locale.US,
                    "Policy Hit Ratio not fulfilled - Expected greater or equal %.2f but got %.2f", policyHitRatio,
                    ratio));
            return false;
        } else {
            return true;
        }
    }

    private boolean checkPolicyConditionRatio(Collection<PolicyConditionHit> targets, float ratio) {
        if (targets.isEmpty()) {
            getLog().info("There are no PolicyConditions to hit");
            return true;
        }

        getLog().info("Policy Condition Hit Ratio is: " + ratio);

        if (ratio < policyConditionHitRatio) {
            getLog().error(String.format(Locale.US,
                    "Policy Condition Hit Ratio not fulfilled - Expected greater or equal %.2f but got %.2f",
                    policyConditionHitRatio, ratio));
            return false;
        } else {
            return true;
        }
    }

}
