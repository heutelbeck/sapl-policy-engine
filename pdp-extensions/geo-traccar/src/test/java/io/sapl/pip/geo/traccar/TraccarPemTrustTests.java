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
package io.sapl.pip.geo.traccar;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.attributes.http.BlockingWebClient;
import lombok.val;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Traccar server trust")
class TraccarPemTrustTests {
    @Test
    @DisplayName("server selection and CA rotation remain isolated on an established HTTPS endpoint")
    void whenServerOrCaChangesThenUseSelectedOperatorTrust() throws IOException {
        val root      = new HeldCertificate.Builder().certificateAuthority(1).build();
        val other     = new HeldCertificate.Builder().certificateAuthority(1).build();
        val leaf      = new HeldCertificate.Builder().addSubjectAlternativeName("localhost").signedBy(root).build();
        val handshake = new HandshakeCertificates.Builder().heldCertificate(leaf, root.certificate()).build();
        try (val endpoint = new MockWebServer()) {
            endpoint.useHttps(handshake.sslSocketFactory(), false);
            endpoint.enqueue(new MockResponse().setBody("42"));
            endpoint.start();
            val url         = "https://localhost:" + endpoint.getPort();
            val originals   = variables(url, root.certificatePem(), other.certificatePem());
            val credentials = ObjectValue.builder().put("token", Value.of("test-token")).build();
            val secrets     = ObjectValue.builder()
                    .put("traccar", ObjectValue.builder().put("one", credentials).put("two", credentials).build())
                    .build();
            val ctx         = new AttributeAccessContext(originals, secrets, Value.EMPTY_OBJECT);
            val pip         = new TraccarPolicyInformationPoint(
                    BlockingWebClient.withDefaults(JsonMapper.builder().build()));
            try (val stream = pip.server(ctx)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isEqualTo(Value.of(42)));
            }
            try (val stream = pip.server(ctx, (TextValue) Value.of("two"))) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
            val rotated = new AttributeAccessContext(variables(url, other.certificatePem(), root.certificatePem()),
                    secrets, originals);
            try (val stream = pip.server(rotated)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(10))
                        .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
            }
            assertThat(endpoint.getRequestCount()).isOne();
        }
    }

    private static ObjectValue variables(String url, String first, String second) {
        val profiles = ObjectValue.builder().put("first", profile(first)).put("second", profile(second)).build();
        val servers  = ObjectValue.builder().put("one", server(url, "first")).put("two", server(url, "second")).build();
        return ObjectValue.builder().put("tlsTrustProfiles", profiles)
                .put("traccar", ObjectValue.builder().put("default", Value.of("one")).put("servers", servers).build())
                .build();
    }

    private static ObjectValue server(String url, String profile) {
        return ObjectValue.builder().put("baseUrl", Value.of(url)).put("trustProfile", Value.of(profile)).build();
    }

    private static ObjectValue profile(String pem) {
        return ObjectValue.builder().put("trustedCaPem", Value.of(pem)).build();
    }
}
