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
package io.sapl.pdp;

import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.compiler.document.TracedVote;
import io.sapl.pdp.configuration.ConfigurationIds;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.interceptors.ReportBuilderUtil;
import io.sapl.pdp.interceptors.VoteReport;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static io.sapl.api.pdp.StreamingPolicyDecisionPoint.DEFAULT_PDP_ID;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end provenance tests: for every source mode a full
 * decide-and-intercept run asserts that the {@link VoteReport} carries
 * exactly the configuration id of the configuration that produced the
 * decision (the manifest id for bundles, the independently recomputed
 * content derivation otherwise).
 */
@DisplayName("Configuration id provenance in decision reports")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ConfigurationIdProvenanceTests {

    private static final String PERMIT_READ_POLICY = """
            policy "permit-read"
            permit
                action == "read";
            """;

    private static final String PDP_JSON = """
            { "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "DENY", "errorHandling": "ABSTAIN" } }
            """;

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of(Value.of("alice"),
            Value.of("read"), Value.of("archive"), Value.NULL);

    private final List<VoteReport> reports = new CopyOnWriteArrayList<>();

    @Test
    @DisplayName("bundle source: the report carries the manifest configuration id")
    void whenDecidingWithBundleSourceThenReportCarriesManifestId() throws Exception {
        val bundleBytes = BundleBuilder.create().withPdpJson(PDP_JSON)
                .withPolicy("permit-read.sapl", PERMIT_READ_POLICY).withConfigurationId("provenance-bundle-id").build();
        val builder     = PolicyDecisionPointBuilder.withDefaults().withBundle(bundleBytes, DEFAULT_PDP_ID,
                BundleSecurityPolicy.builder().disableSignatureVerification().build());

        val report = decideAndCaptureReport(builder);

        assertThat(report.configurationId()).isEqualTo("provenance-bundle-id");
    }

    @Test
    @DisplayName("directory source: the report carries the recomputed dir:<dirName>@<hash16> id")
    void whenDecidingWithDirectorySourceThenReportCarriesDerivedId(@TempDir Path policyDir) throws Exception {
        Files.writeString(policyDir.resolve("pdp.json"), PDP_JSON);
        Files.writeString(policyDir.resolve("permit-read.sapl"), PERMIT_READ_POLICY);
        val expectedId = ConfigurationIds.derive(
                "dir:" + requireNonNull(policyDir.toAbsolutePath().normalize().getFileName()),
                utf8Contents(Map.of("pdp.json", PDP_JSON, "permit-read.sapl", PERMIT_READ_POLICY)));
        val builder    = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(policyDir);

        val report = decideAndCaptureReport(builder);

        assertThat(report.configurationId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("resources source: the report carries the recomputed res:root@<hash16> id")
    void whenDecidingWithResourcesSourceThenReportCarriesDerivedId() throws Exception {
        val pdpJson    = resourceText("/single-pdp-policies/pdp.json");
        val policy     = resourceText("/single-pdp-policies/archives.sapl");
        val expectedId = ConfigurationIds.derive("res:root",
                utf8Contents(Map.of("pdp.json", pdpJson, "archives.sapl", policy)));
        val builder    = PolicyDecisionPointBuilder.withDefaults().withResourcesSource("/single-pdp-policies");

        val report = decideAndCaptureReport(builder);

        assertThat(report.configurationId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("embedded source: the report carries the recomputed embedded@<hash16> id")
    void whenDecidingWithEmbeddedPoliciesThenReportCarriesDerivedId() throws Exception {
        val equivalent = new PDPConfiguration("default", "unidentified", CombiningAlgorithm.DEFAULT,
                List.of(PERMIT_READ_POLICY), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        val expectedId = ConfigurationIds.derive("embedded", ConfigurationIds.entriesOf(equivalent));
        val builder    = PolicyDecisionPointBuilder.withDefaults().withPolicy(PERMIT_READ_POLICY);

        val report = decideAndCaptureReport(builder);

        assertThat(report.configurationId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("the report JSON always contains the configurationId field")
    void whenRenderingReportAsValueThenConfigurationIdFieldIsPresent() throws Exception {
        val builder = PolicyDecisionPointBuilder.withDefaults().withPolicy(PERMIT_READ_POLICY);

        val report      = decideAndCaptureReport(builder);
        val reportValue = ReportBuilderUtil.toObjectValue(report);

        assertThat(reportValue.get("configurationId")).isEqualTo(Value.of(report.configurationId()));
    }

    private VoteReport decideAndCaptureReport(PolicyDecisionPointBuilder builder) throws Exception {
        val capture = (DecisionInterceptor) (decision, timestamp, subscriptionId, authorizationSubscription) -> {
            if (decision instanceof TracedVote tracedVote) {
                reports.add(ReportBuilderUtil.extractReport(tracedVote, subscriptionId, authorizationSubscription));
            }
        };
        try (val components = builder.withDecisionInterceptor(capture).build()) {
            components.pdp().decideOnce(SUBSCRIPTION);
        }
        assertThat(reports).isNotEmpty();
        return reports.getLast();
    }

    private static Map<String, byte[]> utf8Contents(Map<String, String> textByName) {
        val contents = new TreeMap<String, byte[]>();
        for (val entry : textByName.entrySet()) {
            contents.put(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return contents;
    }

    private String resourceText(String resourcePath) {
        try (val stream = requireNonNull(getClass().getResourceAsStream(resourcePath))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
