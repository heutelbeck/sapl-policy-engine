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
package io.sapl.mavenplugin.test.coverage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.helper.CoverageAPIHelper;
import io.sapl.mavenplugin.test.coverage.helper.CoverageRatioCalculator;
import io.sapl.mavenplugin.test.coverage.helper.CoverageTargetHelper;
import io.sapl.mavenplugin.test.coverage.helper.SaplDocumentReader;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.report.GenericCoverageReporter;
import io.sapl.mavenplugin.test.coverage.report.html.HtmlLineCoverageReportGenerator;
import io.sapl.mavenplugin.test.coverage.report.sonar.SonarLineCoverageReportGenerator;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

class ReportCoverageInformationMojoTests extends AbstractMojoTestCase {

    private SaplDocumentReader saplDocumentReader;

    private CoverageTargetHelper coverageTargetHelper;

    private CoverageAPIHelper coverageAPIHelper;

    private CoverageRatioCalculator ratioCalculator;

    private SonarLineCoverageReportGenerator sonarReporter;

    private HtmlLineCoverageReportGenerator htmlReporter;

    private Log log;

    private CoverageTargets coverageTargets_twoSets_two_Policies_twoConditions;

    @BeforeEach
    void setup() throws Exception {
        super.setUp();
        saplDocumentReader   = mock(SaplDocumentReader.class);
        coverageTargetHelper = mock(CoverageTargetHelper.class);
        coverageAPIHelper    = mock(CoverageAPIHelper.class);
        ratioCalculator      = mock(CoverageRatioCalculator.class);
        GenericCoverageReporter reporter = mock(GenericCoverageReporter.class);
        sonarReporter = mock(SonarLineCoverageReportGenerator.class);
        htmlReporter  = mock(HtmlLineCoverageReportGenerator.class);
        log           = mock(Log.class);

        getContainer().addComponent(saplDocumentReader, SaplDocumentReader.class, "");
        getContainer().addComponent(coverageTargetHelper, CoverageTargetHelper.class, "");
        getContainer().addComponent(coverageAPIHelper, CoverageAPIHelper.class, "");
        getContainer().addComponent(ratioCalculator, CoverageRatioCalculator.class, "");
        getContainer().addComponent(reporter, GenericCoverageReporter.class, "");
        getContainer().addComponent(sonarReporter, SonarLineCoverageReportGenerator.class, "");
        getContainer().addComponent(htmlReporter, HtmlLineCoverageReportGenerator.class, "");

        coverageTargets_twoSets_two_Policies_twoConditions = new CoverageTargets(
                List.of(new PolicySetHit("set1"), new PolicySetHit("set2")),
                List.of(new PolicyHit("set1", "policy1"), new PolicyHit("set2", "policy2")),
                List.of(new PolicyConditionHit("set1", "policy1", 1, true),
                        new PolicyConditionHit("set2", "policy2", 1, true)));
    }

    @Test
    void test_disableCoverage() throws Exception {

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_coverageDisabled.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, () -> mojo.execute());
    }

    @Test
    void test_DskipTestsFail() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);
        mojo.setSkipTests(true);
        mojo.setFailOnDisabledTests(true);
        assertThrows(MojoFailureException.class, () -> mojo.execute());

        verify(log, atLeastOnce())
                .error("Tests were skipped, but the sapl-maven-plugin is configured to enforce tests to be run.");
    }

    @Test
    void test_DskipTestsNoFail() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);
        mojo.setSkipTests(true);
        mojo.setFailOnDisabledTests(false);
        assertDoesNotThrow(() -> mojo.execute());

        verify(log, atLeastOnce()).info(
                "Tests disabled. Skipping coverage validation requirements validation. If you want the build to fail in this case, set the sapl-maven-plugin configuration parameter 'failOnDisabledTests' to true.");
    }

    @Test
    void test_DMavenTestSkipFail() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);
        mojo.setMavenTestSkip(true);
        mojo.setFailOnDisabledTests(true);
        assertThrows(MojoFailureException.class, () -> mojo.execute());

        verify(log, atLeastOnce())
                .error("Tests were skipped, but the sapl-maven-plugin is configured to enforce tests to be run.");
    }

    @Test
    void test_DMavenTestSkipNoFail() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);
        mojo.setMavenTestSkip(true);
        mojo.setFailOnDisabledTests(false);
        assertDoesNotThrow(() -> mojo.execute());

        verify(log, atLeastOnce()).info(
                "Tests disabled. Skipping coverage validation requirements validation. If you want the build to fail in this case, set the sapl-maven-plugin configuration parameter 'failOnDisabledTests' to true.");
    }

    @Test
    void test_readError() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenThrow(new IOException("TESTING"));
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);
        assertThrows(MojoFailureException.class, () -> mojo.execute());

        verify(log, atLeastOnce()).error("Error test report data. IOException: TESTING");
    }

    @Test
    void test_happyPath() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        mojo.execute();

        verifyReporterAreCalled();

        verify(log).info("All coverage criteria passed.");
    }

    @Test
    void test_policyConditionHitCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 50f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Condition Hit Ratio not fulfilled. Expected greater or equal 80.00 but got 50.00");
    }

    @Test
    void test_policyHitCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 50f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
    }

    @Test
    void test_policySetHitCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(50f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Set Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");

    }

    @Test
    void test_policySetHitAndPolicyHitCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(50f, 50f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Set Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
        verify(log).error("Policy Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
    }

    @Test
    void test_policySetHitAndPolicyConditionHitCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(50f, 100f, 50f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Set Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
        verify(log).error("Policy Condition Hit Ratio not fulfilled. Expected greater or equal 80.00 but got 50.00");
    }

    @Test
    void test_policyHitAndPolicyConditionHitCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 50f, 50f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
        verify(log).error("Policy Condition Hit Ratio not fulfilled. Expected greater or equal 80.00 but got 50.00");
    }

    @Test
    void test_allCriteriaNotFulfilled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(50f, 50f, 50f);

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        assertThrows(MojoFailureException.class, mojo::execute);

        verifyReporterAreCalled();

        verify(log).error("Policy Set Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
        verify(log).error("Policy Hit Ratio not fulfilled. Expected greater or equal 100.00 but got 50.00");
        verify(log).error("Policy Condition Hit Ratio not fulfilled. Expected greater or equal 80.00 but got 50.00");
    }

    @Test
    void test_emptyTargets() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        CoverageTargets targets = new CoverageTargets(List.of(), List.of(), List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any())).thenReturn(targets);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(50f, 50f, 50f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        mojo.execute();

        verifyReporterAreCalled();

        verify(log).info("All coverage criteria passed.");
        verify(log).info("There are no PolicySets to hit.");
        verify(log).info("There are no Policies to hit.");
        verify(log).info("There are no PolicyConditions to hit.");
    }

    private void verifyReporterAreCalled() throws MojoExecutionException {

        verify(saplDocumentReader, times(1)).retrievePolicyDocuments(any(), any(), any());
        verify(this.htmlReporter, times(1)).generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat());
        verify(this.sonarReporter, times(1)).generateSonarLineCoverageReport(any(), any(), any(), any(), any());
    }

    @Test
    void test_happyPath_sonarReportDisabled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);
        when(this.htmlReporter.generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(Paths.get("test", "index.html"));

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_sonarReportDisabled.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        mojo.execute();

        verify(saplDocumentReader, times(1)).retrievePolicyDocuments(any(), any(), any());
        verify(this.htmlReporter, times(1)).generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat());
        verify(this.sonarReporter, never()).generateSonarLineCoverageReport(any(), any(), any(), any(), any());

        verify(log).info("All coverage criteria passed.");
    }

    @Test
    void test_happyPath_htmlReportDisabled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_htmlReportDisabled.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        mojo.execute();

        verify(saplDocumentReader, times(1)).retrievePolicyDocuments(any(), any(), any());
        verify(this.htmlReporter, never()).generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat());
        verify(this.sonarReporter, times(1)).generateSonarLineCoverageReport(any(), any(), any(), any(), any());

        verify(log).info("All coverage criteria passed.");
    }

    @Test
    void test_happyPath_allReportsDisabled() throws Exception {

        when(this.saplDocumentReader.retrievePolicyDocuments(any(), any(), any())).thenReturn(List.of());
        when(this.coverageTargetHelper.getCoverageTargets(any()))
                .thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.coverageAPIHelper.readHits(any())).thenReturn(coverageTargets_twoSets_two_Policies_twoConditions);
        when(this.ratioCalculator.calculateRatio(any(), any())).thenReturn(100f, 100f, 100f);

        Path pom  = Paths.get("src", "test", "resources", "pom", "pom_allReportsDisabled.xml");
        var  mojo = (ReportCoverageInformationMojo) lookupMojo("report-coverage-information", pom.toFile());
        mojo.setLog(this.log);

        mojo.execute();

        verify(saplDocumentReader, times(1)).retrievePolicyDocuments(any(), any(), any());
        verify(this.htmlReporter, never()).generateHtmlReport(any(), any(), anyFloat(), anyFloat(), anyFloat());
        verify(this.sonarReporter, never()).generateSonarLineCoverageReport(any(), any(), any(), any(), any());

        verify(log).info("All coverage criteria passed.");
    }

}
