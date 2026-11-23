/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.coverage;

import io.sapl.api.coverage.BranchHit;
import io.sapl.api.coverage.LineCoverageInfo;
import io.sapl.api.coverage.LineCoverageStatus;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.test.SaplTestFixture;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Fixture coverage integration tests")
class FixtureCoverageIntegrationTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Pattern LINE_NUMBER_OF_SONARQUBE = Pattern.compile("lineNumber=\"(\\d+)\"");

    private static final String MISKATONIC_POLICY = """
            policy "miskatonic-library-access"
            permit
                subject.role == "faculty";
                resource.department == "occult studies";
            """;

    @TempDir
    Path tempDir;

    private static AuthorizationSubscription facultySubscription() {
        return AuthorizationSubscription.of(Map.of("role", "faculty"), "read", Map.of("department", "occult studies"));
    }

    @Test
    @DisplayName("collects coverage when enabled")
    void whenCoverageEnabled_thenWritesCoverageFile() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("arkham-library-test").withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        assertThat(coverageFiles).hasSize(1);

        val json = readFirstCoverageRecord(coverageFiles.getFirst());
        assertThat(json.get("testIdentifier").asString()).isEqualTo("arkham-library-test");
        assertThat(json.get("evaluationCount").asInt()).isPositive();
    }

    @Test
    @DisplayName("collects no coverage when disabled")
    void whenCoverageDisabled_thenNoCoverageFile() {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY).withCoverageOutput(tempDir)
                .withCoverageFileWriteDisabled();

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        assertThat(coverageFiles).isEmpty();
    }

    @Test
    @DisplayName("generates test identifier when not provided")
    void whenNoTestIdentifier_thenGeneratesOne() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY).withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        val json          = readFirstCoverageRecord(coverageFiles.getFirst());

        assertThat(json.get("testIdentifier").asString()).isNotEmpty();
    }

    @Test
    @DisplayName("records policy coverage details")
    void whenPolicyEvaluated_thenRecordsPolicyDetails() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("coverage-detail-test").withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        val json          = readFirstCoverageRecord(coverageFiles.getFirst());

        assertThat(json.has("policies")).isTrue();
        val policies = json.get("policies");
        assertThat(policies).isNotEmpty();

        val firstPolicy = policies.get(0);
        assertThat(firstPolicy.get("documentName").asString()).isEqualTo("miskatonic-library-access");
        assertThat(firstPolicy.get("documentType").asString()).isEqualTo("policy");
    }

    @Test
    @DisplayName("records decision counts")
    void whenDecisionsRecorded_thenCountsCorrect() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("decision-count-test").withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        val json          = readFirstCoverageRecord(coverageFiles.getFirst());

        assertThat(json.has("decisions")).isTrue();
        val decisions = json.get("decisions");
        assertThat(decisions.get("PERMIT").asInt()).isPositive();
    }

    @Test
    @DisplayName("accumulates multiple evaluations in single test")
    void whenMultipleEvaluations_thenAccumulatesCoverage() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("multi-eval-test").withCoverageOutput(tempDir);

        // First evaluation - should permit
        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        // Each verify() call writes coverage, so we should have at least one record
        assertThat(coverageFiles).isNotEmpty();
    }

    @Test
    @DisplayName("verify returns TestResult with coverage")
    void whenVerify_thenReturnsTestResultWithCoverage() {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY).withTestIdentifier("result-test")
                .withCoverageOutput(tempDir);

        val result = fixture.whenDecide(facultySubscription()).expectPermit().verify();

        assertThat(result.passed()).isTrue();
        assertThat(result.hasCoverage()).isTrue();
        assertThat(result.coverage()).isNotNull();
        assertThat(result.coverage().getTestIdentifier()).isEqualTo("result-test");
    }

    @Test
    @DisplayName("withCoverageFileWriteDisabled prevents file writing")
    void whenCoverageFileWriteDisabled_thenNoFileWritten() {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("no-file-test").withCoverageOutput(tempDir).withCoverageFileWriteDisabled();

        val result = fixture.whenDecide(facultySubscription()).expectPermit().verify();

        assertThat(result.passed()).isTrue();
        assertThat(result.hasCoverage()).isTrue();
        val coverageFiles = listCoverageFiles(tempDir);
        assertThat(coverageFiles).isEmpty();
    }

    @ParameterizedTest(name = "schema line {0}, matching subject {1}")
    @CsvSource({ "1,true", "1,false", "2,true", "2,false" })
    @DisplayName("counts one schema result and locates the outcome of a reached policy")
    void countsSchemaAndPolicyOutcomes(int schemaLine, boolean matches) throws IOException {
        val policy  = "\n".repeat(schemaLine - 1) + """
                subject enforced schema {"type":"string"}
                  policy "staff" permit
                """;
        val fixture = SaplTestFixture.createSingleTest().withPolicy(policy, "staff.sapl").withCoverageOutput(tempDir);
        val step    = fixture.whenDecide(AuthorizationSubscription.of(matches ? "staff" : 42, "read", "file"));
        if (matches) {
            step.expectPermit().verify();
        } else {
            step.expectNotApplicable().verify();
        }

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        val hits = assertThat(data.getBranchHits()).extracting(BranchHit::statementId, BranchHit::startLine,
                BranchHit::startChar, BranchHit::trueHits, BranchHit::falseHits, BranchHit::totalBranchCount,
                BranchHit::coveredBranchCount);
        if (matches) {
            hits.containsExactlyInAnyOrder(tuple(-1, schemaLine, 0, 1, 0, 2, 1),
                    tuple(BranchHit.POLICY_TWO_BRANCH_ID, schemaLine + 1, 2, 1, 0, 2, 1));
        } else {
            hits.containsExactly(tuple(-1, schemaLine, 0, 0, 1, 2, 1));
        }
    }

    @ParameterizedTest(name = "deny document first {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("attributes each document outcome to the vote of that document")
    void countsBothContributingEffects(boolean denyFirst) throws IOException {
        val permit = "policy \"reader\" permit subject == \"staff\";";
        val deny   = "policy \"hold\" deny";
        SaplTestFixture.createIntegrationTest()
                .withCombiningAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE))
                .withPolicy(denyFirst ? deny : permit).withPolicy(denyFirst ? permit : deny).withCoverageOutput(tempDir)
                .whenDecide(AuthorizationSubscription.of("staff", "read", "file")).expectDeny().verify();

        val report = readFirstCoverageRecord(listCoverageFiles(tempDir).getFirst());
        assertThat(report.path("metrics").path("matchedPolicyCount").asInt()).isEqualTo(2);
        val documents = new CoverageReader(tempDir).readAllRecords().getFirst().getPolicyCoverageList();
        assertThat(documents).hasSize(2)
                .allSatisfy(document -> assertThat(document.getBranchHits()).filteredOn(BranchHit::isPolicyOutcome)
                        .singleElement().extracting(BranchHit::trueHits, BranchHit::falseHits).containsExactly(1, 0));
    }

    @Test
    @DisplayName("counts each reached set member at its declaration even when members share a line")
    void countsMemberOutcomesOnOneLine() throws IOException {
        val source = """
                // member votes differ
                  set "team" priority deny or abstain
                  policy "reader" permit policy "hold" deny
                """;
        SaplTestFixture.createSingleTest().withPolicy(source).withCoverageOutput(tempDir)
                .whenDecide(AuthorizationSubscription.of("staff", "read", "file")).expectDeny().verify();

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        assertThat(data.getBranchHits()).filteredOn(BranchHit::isPolicyOutcome)
                .extracting(BranchHit::startLine, BranchHit::startChar, BranchHit::endChar, BranchHit::trueHits,
                        BranchHit::falseHits)
                .containsExactlyInAnyOrder(tuple(2, 2, 5, 1, 0), tuple(3, 2, 8, 1, 0), tuple(3, 25, 31, 1, 0));
    }

    @ParameterizedTest(name = "schema-only set, matching subject {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("a schema-only set counts the validation once and uses it as the effective target")
    void countsSchemaOnlySetTarget(boolean matches) throws IOException {
        val source  = """
                subject enforced schema {"type":"string"}
                set "staff" priority deny or abstain
                policy "reader" permit
                    action == "read";
                policy "hold" deny
                    resource == "file";
                """;
        val fixture = SaplTestFixture.createSingleTest().withPolicy(source).withCoverageOutput(tempDir);
        val step    = fixture.whenDecide(AuthorizationSubscription.of(matches ? "staff" : 42, "read", "file"));
        if (matches) {
            step.expectDeny().verify();
        } else {
            step.expectNotApplicable().verify();
        }

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        assertThat(data).extracting(PolicyCoverageData::getTargetTrueHits, PolicyCoverageData::getTargetFalseHits)
                .containsExactly(matches ? 1 : 0, matches ? 0 : 1);
        val hits = assertThat(data.getBranchHits()).extracting(BranchHit::startLine, BranchHit::statementId,
                BranchHit::trueHits, BranchHit::falseHits);
        if (matches) {
            hits.containsExactlyInAnyOrder(tuple(1, -1, 1, 0), tuple(2, BranchHit.POLICY_SINGLE_BRANCH_ID, 1, 0),
                    tuple(3, BranchHit.POLICY_TWO_BRANCH_ID, 1, 0), tuple(4, 0, 1, 0),
                    tuple(5, BranchHit.POLICY_TWO_BRANCH_ID, 1, 0), tuple(6, 0, 1, 0));
        } else {
            hits.containsExactly(tuple(1, -1, 0, 1));
        }
    }

    @Test
    @DisplayName("a schema evaluation error covers neither boolean branch")
    void retainsSchemaErrorsWithoutBooleanHits() throws IOException {
        val source = """
                environment enforced schema {"type":"null"}
                policy "read" permit
                """;
        SaplTestFixture.createSingleTest().withPolicy(source, "read.sapl").withCoverageOutput(tempDir)
                .whenDecide(AuthorizationSubscription.of("staff", "read", "file")).expectIndeterminate().verify();

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        assertThat(data.getBranchHits())
                .extracting(BranchHit::startLine, BranchHit::trueHits, BranchHit::falseHits, BranchHit::errorHits,
                        BranchHit::coveredBranchCount, BranchHit::totalBranchCount)
                .containsExactlyInAnyOrder(tuple(1, 0, 0, 1, 0, 2), tuple(2, 0, 0, 1, 0, 2));
        assertThat(new SonarQubeCoverageReportGenerator(tempDir).generate())
                .contains("lineNumber=\"1\" covered=\"false\" branchesToCover=\"2\" coveredBranches=\"0\"");
    }

    @ParameterizedTest(name = "authored body condition {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("information-only schemas and variable declarations add no decision branch")
    void skipsAnnotationOnlySchema(boolean authoredCondition) throws IOException {
        val policy = """
                subject schema {"type":"string"}
                policy "staff" permit
                var user = subject;
                """ + (authoredCondition ? "true;" : "");
        SaplTestFixture.createSingleTest().withPolicy(policy).withCoverageOutput(tempDir)
                .whenDecide(AuthorizationSubscription.of(42, "read", "file")).expectPermit().verify();

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        val hits = assertThat(data.getBranchHits()).extracting(BranchHit::statementId, BranchHit::startLine,
                BranchHit::trueHits, BranchHit::totalBranchCount);
        if (authoredCondition) {
            hits.containsExactlyInAnyOrder(tuple(BranchHit.POLICY_TWO_BRANCH_ID, 2, 1, 2), tuple(0, 4, 1, 2));
        } else {
            hits.containsExactly(tuple(BranchHit.POLICY_SINGLE_BRANCH_ID, 2, 1, 1));
        }
    }

    @ParameterizedTest(name = "subject matches the schema {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("a constant false target prevents the set schema and member from being reached")
    void reportsOnlyTheConstantFalseTarget(boolean matchesSchema) throws IOException {
        val set = """
                subject enforced schema {"type":"string"}
                set "staff" first or abstain
                    for false
                policy "reader" permit
                """;
        SaplTestFixture.createSingleTest().withPolicy(set).withCoverageOutput(tempDir)
                .whenDecide(AuthorizationSubscription.of(matchesSchema ? "staff" : 42, "go", "file"))
                .expectNotApplicable().verify();

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        assertThat(data.getTargetFalseHits()).isOne();
        assertThat(data.getBranchHits()).extracting(BranchHit::startLine, BranchHit::startChar, BranchHit::falseHits)
                .containsExactly(tuple(3, 8, 1));
    }

    @ParameterizedTest(name = "schema matches {0}, for-clause would match {1}")
    @CsvSource({ "true,true", "true,false", "false,true" })
    @DisplayName("the explicit target reports the conjunction of validation and the for-clause")
    void countsTheEffectiveTargetResult(boolean schemaMatches, boolean forMatches) throws IOException {
        val set     = """
                subject enforced schema {"type":"string"}
                set "staff" first or abstain
                    for action == "go"
                policy "reader" permit
                """;
        val fixture = SaplTestFixture.createSingleTest().withPolicy(set).withCoverageOutput(tempDir);
        val step    = fixture.whenDecide(
                AuthorizationSubscription.of(schemaMatches ? "staff" : 42, forMatches ? "go" : "stop", "file"));
        val matches = schemaMatches && forMatches;
        if (matches) {
            step.expectPermit().verify();
        } else {
            step.expectNotApplicable().verify();
        }

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        assertThat(data).extracting(PolicyCoverageData::getTargetTrueHits, PolicyCoverageData::getTargetFalseHits)
                .containsExactly(matches ? 1 : 0, matches ? 0 : 1);
        val hits = assertThat(data.getBranchHits()).extracting(BranchHit::startLine, BranchHit::trueHits,
                BranchHit::falseHits);
        if (matches) {
            hits.containsExactlyInAnyOrder(tuple(1, 1, 0), tuple(2, 1, 0), tuple(3, 1, 0), tuple(4, 1, 0));
        } else if (forMatches) {
            hits.containsExactlyInAnyOrder(tuple(1, 0, 1), tuple(3, 0, 1));
        } else {
            hits.containsExactly(tuple(3, 0, 1));
        }
    }

    @ParameterizedTest(name = "schema error with explicit for-clause {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("a set validation error reaches the effective target without covering either boolean result")
    void countsSetSchemaErrors(boolean explicitTarget) throws IOException {
        val set = """
                environment enforced schema {"type":"null"}
                set "staff" first or abstain, errors propagate
                """ + (explicitTarget ? "for action == \"go\"\n" : "") + "policy \"reader\" permit";
        SaplTestFixture.createSingleTest().withPolicy(set).withCoverageOutput(tempDir)
                .whenDecide(AuthorizationSubscription.of("staff", "go", "file")).expectIndeterminate().verify();

        val data = new CoverageReader(tempDir).readAggregated().getPolicyCoverageList().getFirst();
        assertThat(data)
                .extracting(PolicyCoverageData::getTargetTrueHits, PolicyCoverageData::getTargetFalseHits,
                        PolicyCoverageData::getTargetErrorHits, PolicyCoverageData::wasTargetEvaluated)
                .containsExactly(0, 0, 1, true);
        assertThat(data.getBranchHits()).hasSize(explicitTarget ? 3 : 2).allSatisfy(hit -> assertThat(hit)
                .extracting(BranchHit::errorHits, BranchHit::coveredBranchCount).containsExactly(1, 0));
    }

    static Stream<Arguments> lineEnds() {
        return Stream.of(arguments("a line feed", "\n"), arguments("a carriage return", "\r"),
                arguments("a carriage return and a line feed", "\r\n"));
    }

    @ParameterizedTest(name = "lines that end with {0}")
    @MethodSource("lineEnds")
    @DisplayName("reports the policy and its conditions as covered on the lines where an editor shows them, whatever ends the lines")
    void whenLinesEndWithAnyLineBreakThenPolicyAndConditionsAreCoveredOnTheLinesAnEditorShows(String lineBreak,
            String lineEnd) {
        val coverage = coverageOf(lineEnd);

        assertThat(coverage.getCoveredLines()).isEqualTo(Set.of(1, 3, 4));
    }

    @ParameterizedTest(name = "lines that end with {0}")
    @MethodSource("lineEnds")
    @DisplayName("the line coverage the editor receives marks the policy and its conditions on their lines, whatever ends the lines")
    void whenLinesEndWithAnyLineBreakThenLineCoverageForTheEditorMarksTheLinesAnEditorShows(String lineBreak,
            String lineEnd) {
        val coverage = coverageOf(lineEnd);

        // The editor shows every line whose status is not irrelevant.
        assertThat(coverage.getLineCoverage()).filteredOn(line -> line.status() != LineCoverageStatus.IRRELEVANT)
                .extracting(LineCoverageInfo::line).containsExactly(1, 3, 4);
    }

    @ParameterizedTest(name = "lines that end with {0}")
    @MethodSource("lineEnds")
    @DisplayName("the SonarQube report names the lines of the policy and its conditions, whatever ends the lines")
    void whenLinesEndWithAnyLineBreakThenSonarQubeReportNamesTheLinesAnEditorShows(String lineBreak, String lineEnd)
            throws IOException {
        SaplTestFixture.createSingleTest().withPolicy(policyWithLinesEndingIn(lineEnd), "policies/miskatonic.sapl")
                .withTestIdentifier("sonarqube-line-end-test").withCoverageOutput(tempDir)
                .whenDecide(facultySubscription()).expectPermit().verify();

        val report = new SonarQubeCoverageReportGenerator(tempDir).generate();

        assertThat(LINE_NUMBER_OF_SONARQUBE.matcher(report).results().map(match -> Integer.valueOf(match.group(1))))
                .containsExactly(1, 3, 4);
    }

    private PolicyCoverageData coverageOf(String lineEnd) {
        val result = SaplTestFixture.createSingleTest().withPolicy(policyWithLinesEndingIn(lineEnd))
                .withTestIdentifier("line-end-test").withCoverageOutput(tempDir).withCoverageFileWriteDisabled()
                .whenDecide(facultySubscription()).expectPermit().verify();
        // The fixture collects coverage, so a verified test carries it.
        val coverage = requireNonNull(result.coverage());
        assertThat(coverage.getPolicyCoverageList()).hasSize(1);
        return coverage.getPolicyCoverageList().getFirst();
    }

    private static String policyWithLinesEndingIn(String lineEnd) {
        return MISKATONIC_POLICY.replace("\n", lineEnd);
    }

    private List<Path> listCoverageFiles(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return List.of();
            }
            try (val stream = Files.list(directory)) {
                return stream.filter(path -> path.toString().endsWith(".ndjson")).toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private JsonNode readFirstCoverageRecord(Path coverageFile) throws IOException {
        val content = Files.readString(coverageFile);
        val lines   = content.trim().split(System.lineSeparator());
        return MAPPER.readTree(lines[0]);
    }
}
