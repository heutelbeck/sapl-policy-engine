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
package io.sapl.pdp.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.pdp.remote.RemoteHttpReactivePolicyDecisionPoint.RemoteHttpPolicyDecisionPointBuilder;
import lombok.val;

@DisplayName("HTTP remote PDP builder transport security")
class RemoteHttpBuilderSecurityTests {

    private static Stream<Arguments> credentialAppliers() {
        return Stream.of(
                arguments("basicAuth",
                        (UnaryOperator<RemoteHttpPolicyDecisionPointBuilder>) b -> b.basicAuth("user", "secret")),
                arguments("apiKey", (UnaryOperator<RemoteHttpPolicyDecisionPointBuilder>) b -> b.apiKey("token")));
    }

    @ParameterizedTest(name = "{0} over plaintext http is refused")
    @MethodSource("credentialAppliers")
    @DisplayName("credentials over plaintext http are refused by default (fail closed)")
    void whenCredentialsWithoutTlsThenBuildFailsClosed(String name,
            UnaryOperator<RemoteHttpPolicyDecisionPointBuilder> credential) {
        val builder = credential.apply(RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost"));

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class).hasMessageContaining("plaintext");
    }

    @ParameterizedTest(name = "{0} over https builds")
    @MethodSource("credentialAppliers")
    @DisplayName("credentials over https build successfully")
    void whenCredentialsWithHttpsThenBuildSucceeds(String name,
            UnaryOperator<RemoteHttpPolicyDecisionPointBuilder> credential) {
        val pdp = credential.apply(RemotePolicyDecisionPoint.builder().http().baseUrl("https://localhost")).build();

        assertThat(pdp).isNotNull();
    }

    @ParameterizedTest(name = "{0} over plaintext http with opt-in builds")
    @MethodSource("credentialAppliers")
    @DisplayName("credentials over plaintext http build only with the explicit insecure opt-in")
    void whenCredentialsPlaintextWithOptInThenBuildSucceeds(String name,
            UnaryOperator<RemoteHttpPolicyDecisionPointBuilder> credential) {
        val pdp = credential.apply(RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost"))
                .allowInsecureTransport().build();

        assertThat(pdp).isNotNull();
    }

    @Test
    @DisplayName("a connection without credentials does not require TLS")
    void whenNoCredentialsThenBuildSucceedsWithoutTls() {
        val pdp = RemotePolicyDecisionPoint.builder().http().baseUrl("http://localhost").build();

        assertThat(pdp).isNotNull();
    }
}
