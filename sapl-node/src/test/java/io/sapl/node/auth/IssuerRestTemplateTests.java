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
package io.sapl.node.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import lombok.val;

@DisplayName("IssuerRestTemplate")
class IssuerRestTemplateTests {

    private static final String ISSUER     = "https://idp.example.org/realms/sapl";
    private static final String DISCOVERY  = ISSUER + "/.well-known/openid-configuration";
    private static final String NO_ADDRESS = "The discovery document names no https address of the keys, nor an http one where http is allowed.";
    private static final String KEY_SET    = keySet();

    private static String keySet() {
        try {
            return new JWKSet(new ECKeyGenerator(Curve.P_256).algorithm(JWSAlgorithm.ES256).generate().toPublicJWK())
                    .toString();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder decoderDiscovering(String keyAddressMember,
            boolean allowInsecureIssuer) {
        return decoderDiscovering(keyAddressMember, allowInsecureIssuer, null);
    }

    // Spring reads the discovery document and, when it builds the decoder, the
    // keys, to learn their algorithms.
    private static NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder decoderDiscovering(String keyAddressMember,
            boolean allowInsecureIssuer, @Nullable String servedKeyAddress) {
        val template = new IssuerRestTemplate(allowInsecureIssuer);
        val server   = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo(DISCOVERY)).andRespond(
                withSuccess("{ \"issuer\": \"" + ISSUER + "\"" + keyAddressMember + " }", MediaType.APPLICATION_JSON));
        if (servedKeyAddress != null) {
            server.expect(requestTo(servedKeyAddress)).andRespond(withSuccess(KEY_SET, MediaType.APPLICATION_JSON));
        }
        return NimbusJwtDecoder.withIssuerLocation(ISSUER).restOperations(template);
    }

    @ParameterizedTest(name = "jwks_uri {0}, opt-in {1}")
    @CsvSource({ "http://idp.example.org/keys, false", "ftp://idp.example.org/keys, true",
            "idp.example.org/keys, false", "https:///keys, false" })
    @DisplayName("a discovery document whose address of the keys is not https, or http where allowed, is refused before keys are fetched")
    void whenKeyAddressIsNotAllowedThenDiscoveryRefused(String keyAddress, boolean allowInsecureIssuer) {
        val builder = decoderDiscovering(", \"jwks_uri\": \"" + keyAddress + "\"", allowInsecureIssuer);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class).hasMessage(NO_ADDRESS);
    }

    @Test
    @DisplayName("a discovery document that names no address of the keys is refused")
    void whenKeyAddressIsMissingThenDiscoveryRefused() {
        val builder = decoderDiscovering("", false);

        assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class).hasMessage(NO_ADDRESS);
    }

    @ParameterizedTest(name = "jwks_uri {0}, opt-in {1}")
    @CsvSource({ "https://idp.example.org/keys, false", "http://idp.example.org/keys, true" })
    @DisplayName("a discovery document with an https address of the keys, or an http one where allowed, is accepted")
    void whenKeyAddressIsAllowedThenDecoderIsBuilt(String keyAddress, boolean allowInsecureIssuer) {
        val builder = decoderDiscovering(", \"jwks_uri\": \"" + keyAddress + "\"", allowInsecureIssuer, keyAddress);

        assertThatCode(builder::build).doesNotThrowAnyException();
    }
}
