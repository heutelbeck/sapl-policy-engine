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
package io.sapl.pdp.configuration;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;
import lombok.val;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.api.pdp.AuthorizationSubscription.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Outbound endpoints are validated before PDP activation")
class OutboundEndpointConfigurationTests {
    private static final ObjectValue PROFILE  = ObjectValue.builder().put("trustedCaPem",
            Value.of(new HeldCertificate.Builder().certificateAuthority(1).build().certificatePem())).build();
    private static final ObjectValue PROFILES = ObjectValue.builder().put("fleet", PROFILE).build();

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigurations")
    @DisplayName("invalid outbound configuration keeps the last successful configuration serving")
    void whenReloadFailsThenRetainActiveConfiguration(String description, String variables, String secrets) {
        try (val source = new PdpVoterSource(new StaticPluginsSource(new PluginsBundle(SaplTesting.FUNCTION_BROKER)),
                Clock.systemUTC())) {
            source.loadConfiguration(configuration("original", new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT)));
            val served    = source.getCurrentConfiguration("test").orElseThrow();
            val candidate = ObjectValue.builder().put("tlsTrustProfiles", PROFILES)
                    .putAll((ObjectValue) json(variables)).build();
            source.loadConfiguration(configuration("invalid", new PdpData(candidate, (ObjectValue) json(secrets))));
            assertThat(source.getCurrentConfiguration("test")).containsSame(served);
            assertThat(source.getPdpStatus("test")).hasValueSatisfying(status -> {
                assertThat(status.state()).isEqualTo(PdpState.STALE);
                assertThat(status.configurationId()).isEqualTo("original");
                assertThat(status.lastError()).doesNotContain("BEGIN CERTIFICATE", "private-test-credential");
            });
        }
    }

    static Stream<Arguments> invalidConfigurations() {
        return Stream.of(arguments("unused malformed certificate", """
                {"tlsTrustProfiles":{"unused":{"trustedCaPem":"bad certificate"}}}
                """, "{}"), arguments("profile without PEM", """
                {"tlsTrustProfiles":{"fleet":{"includeDefaultTrustRoots":true}}}
                """, "{}"), arguments("unexpected profile key", """
                {"tlsTrustProfiles":{"unused":{"trustedCaPem":"...", "typo":true}}}
                """, "{}"), arguments("unknown MQTT trust", """
                {"mqtt":{"brokers":{"b":{"tls":true,"trustProfile":"missing"}}}}
                """, "{}"), arguments("non-boolean MQTT TLS", """
                {"mqtt":{"brokers":{"b":{"tls":"true","trustProfile":"fleet"}}}}
                """, "{}"),
                arguments("unselected Traccar endpoint on plaintext",
                        """
                                {"traccar":{"servers":{"s":{"baseUrl":"http://localhost","allowInsecureHttp":true,"trustProfile":"fleet"}}}}
                                """,
                        "{}"),
                arguments("HTTP service includes plaintext URL",
                        """
                                {"http":{"services":{"s":{"allowedBaseUrls":["https://localhost","ws://localhost"],"trustProfile":"fleet"}}}}
                                """,
                        "{}"),
                arguments("JWT trust cannot use a plaintext URI",
                        """
                                {"jwt":{"publicKeyServer":{"uri":"http://localhost/{kid}","allowInsecureHttp":true,"trustProfile":"fleet"}}}
                                """,
                        "{}"),
                arguments("JWT template cannot select an authority", """
                        {"jwt":{"publicKeyServer":{"uri":"https://{kid}.example/"}}}
                        """, "{}"), arguments("explicit default missing", """
                        {"traccar":{"default":"absent","servers":{}}}
                        """, "{}"), arguments("old MQTT config", """
                        {"mqtt":{"brokerConfig":[]}}
                        """, "{}"), arguments("old Traccar config", """
                        {"TRACCAR_CONFIG":{"baseUrl":"https://example.test"}}
                        """, "{}"), arguments("flat authentication", "{}", """
                        {"mqtt":{"password":"private-test-credential"}}
                        """), arguments("inline endpoint credentials", """
                        {"mqtt":{"brokers":{"b":{"username":"private-test-credential"}}}}
                        """, "{}"), arguments("token query on Traccar endpoint", """
                        {"traccar":{"servers":{"s":{"baseUrl":"https://example.test/?token=private-test-credential"}}}}
                        """, "{}"), arguments("secret trust", "{}", """
                        {"http":{"s":{"trustedCaPem":"secret"}}}
                        """),
                arguments("HTTP service scope with query",
                        """
                                {"http":{"services":{"s":{"allowedBaseUrls":["https://example.test/?token=private-test-credential"]}}}}
                                """,
                        "{}"));
    }

    @ParameterizedTest(name = "configured service = {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("HTTP credentials retain their independent destination allowlist")
    void whenCredentialHasDestinationScopeThenConfigurationActivates(boolean serviceConfigured) {
        val service   = (ObjectValue) json("""
                {"services":{"fleet":{"allowedBaseUrls":["https://example.test/api"],"trustProfile":"fleet"}}}
                """);
        val variables = ObjectValue.builder().put("tlsTrustProfiles", PROFILES);
        if (serviceConfigured) {
            variables.put("http", service);
        }
        val secrets = (ObjectValue) json(
                """
                        {"http":{"fleet":{"headers":{"Authorization":"private-test-credential"},"allowedBaseUrls":["https://example.test/api"]}}}
                        """);
        try (val source = new PdpVoterSource(new StaticPluginsSource(new PluginsBundle(SaplTesting.FUNCTION_BROKER)),
                Clock.systemUTC())) {
            source.loadConfiguration(configuration("valid", new PdpData(variables.build(), secrets)));
            assertThat(source.getPdpStatus("test"))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.LOADED));
        }
    }

    @Test
    @DisplayName("a policy can read application metadata from a named MQTT broker")
    void whenBrokerContainsApplicationMetadataThenPolicyCanReadIt() {
        val variables = (ObjectValue) json("""
                {"mqtt":{"brokers":{"east":{"tlsTrustStore":{"label":"archive"},"tlsTrustStorePassword":42}}}}
                """);
        val policy    = """
                policy "archive access" permit
                mqtt.brokers.east.tlsTrustStore.label == resource;
                mqtt.brokers.east.tlsTrustStorePassword == 42;
                """;
        try (val components = PolicyDecisionPointBuilder.withoutDefaults().build()) {
            components.pdpVoterSource().loadConfiguration(new PDPConfiguration("test", "site",
                    CombiningAlgorithm.DEFAULT, List.of(policy), new PdpData(variables, Value.EMPTY_OBJECT)));
            assertThat(components.pdp().decideOnce(of("alice", "read", "archive"), "test"))
                    .isEqualTo(AuthorizationDecision.PERMIT);
        }
    }

    private static PDPConfiguration configuration(String id, PdpData data) {
        return new PDPConfiguration("test", id, CombiningAlgorithm.DEFAULT, List.of("policy \"test\" permit"), data);
    }
}
