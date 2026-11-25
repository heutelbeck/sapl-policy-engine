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
package io.sapl.test;

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.api.stream.Streams.just;
import static io.sapl.test.Matchers.args;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.QueueStream;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.test.plain.PlainTestAdapter;
import io.sapl.test.plain.SaplDocument;
import io.sapl.test.plain.SaplTestDocument;
import io.sapl.test.plain.TestConfiguration;
import io.sapl.test.plain.TestStatus;

import lombok.val;

@DisplayName("AttributeVerification tests")
class AttributeVerificationTests {

    private static final String LOCATION_POLICY_PATH   = "/attribute-verification-tests/location-policy.sapl";
    private static final String ROLE_POLICY_PATH       = "/attribute-verification-tests/role-policy.sapl";
    private static final String BYPASS_POLICY_PATH     = "/attribute-verification-tests/bypass-policy.sapl";
    private static final String TWO_STREAM_POLICY_PATH = "/attribute-verification-tests/two-stream-policy.sapl";
    private static final String POSITIVE_TESTS_PATH    = "/attribute-verification-tests/attribute-verification-positive.sapltest";
    private static final String NEGATIVE_TESTS_PATH    = "/attribute-verification-tests/attribute-verification-negative.sapltest";

    @Test
    @DisplayName("a changed known mock releases an unneeded pending finder before the script ends")
    void whenKnownMockChangesThenPendingFinderIsReleasedBeforeTheScriptEnds() {
        val pending         = new PendingFinder();
        val closedBeforeEnd = new AtomicBoolean();
        val result          = SaplTestFixture.createSingleTest().withCoverageFileWriteDisabled()
                .withLowLatencyMode(false).withPolicy("policy \"p\" permit <desk.a> == 0 || <waiting.value> == 1;")
                .withPolicyInformationPoint(pending).givenEnvironmentAttribute("a", "desk.a", args(), Value.of(0))
                .whenDecide(AuthorizationSubscription.of("user", "read", "document")).expectPermit()
                .thenEmit("a", Value.of(1)).thenEmit("a", Value.of(0)).expectPermit().thenEmit("a", Value.of(0))
                .expectDecisionMatches(decision -> {
                                        closedBeforeEnd.set(pending.closes.get() == 1);
                                        return decision.decision() == Decision.PERMIT;
                                    })
                .verify(Duration.ofSeconds(1));

        assertThat(result.passed()).isTrue();
        assertThat(pending.opens).hasValue(1);
        assertThat(closedBeforeEnd).isTrue();
    }

    @PolicyInformationPoint(name = "waiting")
    public static class PendingFinder {
        private final AtomicInteger opens  = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        @EnvironmentAttribute(docs = "A local source that has no initial observation.")
        public Stream<Value> value() {
            opens.incrementAndGet();
            val source = new QueueStream<Value>();
            source.onClose(closes::incrementAndGet);
            return source;
        }
    }

    @Test
    void allPositiveAttributeVerificationTestsShouldPass() {
        var locationPolicy  = loadResource(LOCATION_POLICY_PATH);
        var rolePolicy      = loadResource(ROLE_POLICY_PATH);
        var bypassPolicy    = loadResource(BYPASS_POLICY_PATH);
        var twoStreamPolicy = loadResource(TWO_STREAM_POLICY_PATH);
        var tests           = loadResource(POSITIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(SaplDocument.of("location-policy", locationPolicy),
                        SaplDocument.of("role-policy", rolePolicy), SaplDocument.of("bypass-policy", bypassPolicy),
                        SaplDocument.of("two-stream-policy", twoStreamPolicy)))
                .withSaplTestDocuments(List.of(SaplTestDocument.of("positive-tests", tests)))
                .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE)).build();

        var adapter = new PlainTestAdapter();
        var results = adapter.execute(config);

        assertThat(results.allPassed()).withFailMessage(() -> buildFailureMessage(results)).isTrue();
        assertThat(results.passed()).isEqualTo(6);
    }

    @ParameterizedTest(name = "clearance {0}, low-latency mode {1}")
    @CsvSource({ "true,true", "true,false", "false,true", "false,false" })
    @DisplayName("ordinary and covered decisions call exactly the necessary finders")
    void whenCheckingEntryThenBothDecisionsRequestTheSameFinders(boolean clearance, boolean lowLatency) {
        val ordinary = decide(clearance, lowLatency, false);
        val covered  = decide(clearance, lowLatency, true);
        assertThat(covered).isEqualTo(ordinary);
        if (clearance) {
            assertThat(ordinary.decision()).isEqualTo(AuthorizationDecision.PERMIT);
            assertThat(ordinary.calls()).containsExactly("clearance", "duty");
        } else {
            assertThat(ordinary.decision()).isEqualTo(AuthorizationDecision.NOT_APPLICABLE);
            assertThat(ordinary.calls()).containsExactly("clearance");
        }
    }

    private static Result decide(boolean clearance, boolean lowLatency, boolean coverage) {
        val finder        = new EntryFinder(clearance);
        val subscription  = AuthorizationSubscription.of("guard", "enter", "building");
        val configuration = new PDPConfiguration("entrance", "open",
                new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE),
                ObjectValue.builder().put("lowLatencyMode", Value.of(lowLatency)).build(),
                List.of("policy \"access\" permit <entry.clearance>; <entry.duty>;"),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().withPolicyInformationPoint(finder).build()) {
            components.pdpVoterSource().loadConfiguration(configuration);
            val decision = coverage
                    ? components.pdp().decideOnceWithCoverage(subscription, "entrance").vote().authorizationDecision()
                    : components.pdp().decideOnce(subscription, "entrance");
            return new Result(decision, List.copyOf(finder.calls));
        }
    }

    private record Result(AuthorizationDecision decision, List<String> calls) {}

    @PolicyInformationPoint(name = "entry")
    public static class EntryFinder {
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final boolean      cleared;

        EntryFinder(boolean cleared) {
            this.cleared = cleared;
        }

        @EnvironmentAttribute(name = "clearance")
        public Stream<Value> clearance() {
            calls.add("clearance");
            return just(Value.of(cleared));
        }

        @EnvironmentAttribute(name = "duty")
        public Stream<Value> duty() {
            calls.add("duty");
            return just(Value.TRUE);
        }
    }

    @Test
    void allNegativeAttributeVerificationTestsShouldFail() {
        var locationPolicy = loadResource(LOCATION_POLICY_PATH);
        var rolePolicy     = loadResource(ROLE_POLICY_PATH);
        var tests          = loadResource(NEGATIVE_TESTS_PATH);

        var config = TestConfiguration.builder()
                .withSaplDocuments(List.of(SaplDocument.of("location-policy", locationPolicy),
                        SaplDocument.of("role-policy", rolePolicy)))
                .withSaplTestDocuments(List.of(SaplTestDocument.of("negative-tests", tests)))
                .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE)).build();

        var adapter = new PlainTestAdapter();
        var results = adapter.execute(config);

        assertThat(results.allPassed()).isFalse();
        assertThat(results.failed()).isEqualTo(3);

        for (var result : results.scenarioResults()) {
            assertThat(result.status())
                    .withFailMessage("Expected scenario '%s' to fail but it passed", result.scenarioName())
                    .isEqualTo(TestStatus.FAILED);
        }
    }

    private String buildFailureMessage(io.sapl.test.plain.PlainTestResults results) {
        var sb = new StringBuilder("Expected all tests to pass but some failed:\n");
        for (var result : results.scenarioResults()) {
            if (result.status() != TestStatus.PASSED) {
                sb.append("  - ").append(result.requirementName()).append(" > ").append(result.scenarioName())
                        .append(": ").append(result.failureMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    private String loadResource(String path) {
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
