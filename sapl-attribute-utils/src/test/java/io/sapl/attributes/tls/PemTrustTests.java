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
package io.sapl.attributes.tls;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("Operator PEM trust anchors")
class PemTrustTests {
    private static final HeldCertificate FIRST  = new HeldCertificate.Builder().certificateAuthority(1).build();
    private static final HeldCertificate SECOND = new HeldCertificate.Builder().certificateAuthority(1).build();

    @Test
    @DisplayName("an endpoint without a reference uses platform trust")
    void whenReferenceAbsentThenUsePlatformTransport() {
        assertThat(PemTrust.resolve(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT)).isNull();
    }

    private static PemTrust resolve(ObjectValue profile) {
        val profiles  = ObjectValue.builder().put("fleet", profile).build();
        val variables = ObjectValue.builder().put("tlsTrustProfiles", profiles).build();
        val endpoint  = ObjectValue.builder().put("trustProfile", Value.of("fleet")).build();
        return PemTrust.resolve(variables, endpoint);
    }

    @ParameterizedTest(name = "include defaults = {0}")
    @ValueSource(booleans = { false, true })
    @DisplayName("a bundle trusts every supplied root and includes platform roots only on request")
    void whenBundleContainsTwoRootsThenModeSelectsTrustAnchors(boolean include) throws GeneralSecurityException {
        val defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaults.init((KeyStore) null);
        val expected = new HashSet<>(List.of(FIRST.certificate(), SECOND.certificate()));
        if (include) {
            expected.addAll(issuers(defaults));
        }
        val trust = new PemTrust(FIRST.certificatePem() + SECOND.certificatePem(), include);
        assertThat(issuers(trust.trustManagerFactory())).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static List<X509Certificate> issuers(TrustManagerFactory factory) {
        return Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast).flatMap(manager -> Arrays.stream(manager.getAcceptedIssuers()))
                .toList();
    }

    @Test
    @DisplayName("invalid trust option types are rejected")
    void whenTrustFieldsHaveWrongTypesThenFailClosed() {
        val invalidDefault = ObjectValue.builder().put("includeDefaultTrustRoots", Value.of("true")).build();
        val badPem         = ObjectValue.builder().put("trustedCaPem", Value.TRUE).build();
        val badFlag        = ObjectValue.builder().put("trustedCaPem", Value.of(FIRST.certificatePem()))
                .put("includeDefaultTrustRoots", Value.of("true")).build();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(invalidDefault));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(badPem));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(badFlag));
    }

    @ParameterizedTest(name = "invalid bundle {index}")
    @MethodSource("invalidBundles")
    @DisplayName("every block in a configured bundle must be a complete X.509 certificate")
    void whenAnyBlockInvalidThenRejectWholeBundle(String pem) {
        val trust = new PemTrust(pem, true);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(trust::sslContext)
                .withMessage("trustedCaPem must contain a nonempty PEM bundle of X.509 certificates.");
    }

    static Stream<String> invalidBundles() {
        val cert = FIRST.certificatePem();
        return Stream.of("", " ", "junk" + cert, cert + "trailing text", cert + FIRST.privateKeyPkcs8Pem(),
                cert + "-----BEGIN CERTIFICATE-----", cert + """
                        -----BEGIN CERTIFICATE-----
                        !!!!
                        -----END CERTIFICATE-----
                        """, """
                        -----BEGIN CERTIFICATE-----
                        AQID
                        -----END CERTIFICATE-----
                        """);
    }
}
