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
package io.sapl.pdp.configuration.realm;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;

import lombok.val;

/**
 * {@link JWSVerifier} for the {@code EdDSA} JWS algorithm (RFC 8037), backed by
 * the JDK's native EdDSA {@link Signature}, so it needs no Tink dependency.
 * Nimbus' built-in EdDSA verifier requires Google Tink, which {@code sapl-pdp}
 * deliberately avoids; this fills the gap for realm-index verification.
 */
final class EdDsaJwsVerifier implements JWSVerifier {

    private static final String ALGORITHM_EDDSA = "EdDSA";

    private final PublicKey  publicKey;
    private final JCAContext jcaContext = new JCAContext();

    EdDsaJwsVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return Set.of(JWSAlgorithm.EdDSA);
    }

    @Override
    public JCAContext getJCAContext() {
        return jcaContext;
    }

    @Override
    public boolean verify(JWSHeader header, byte[] signingInput, Base64URL signature) throws JOSEException {
        if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) {
            throw new JOSEException("Unsupported JWS algorithm " + header.getAlgorithm() + ", must be EdDSA.");
        }
        try {
            val verifier = Signature.getInstance(ALGORITHM_EDDSA);
            verifier.initVerify(publicKey);
            verifier.update(signingInput);
            return verifier.verify(signature.decode());
        } catch (GeneralSecurityException e) {
            throw new JOSEException("EdDSA signature verification failed: " + e.getMessage(), e);
        }
    }
}
