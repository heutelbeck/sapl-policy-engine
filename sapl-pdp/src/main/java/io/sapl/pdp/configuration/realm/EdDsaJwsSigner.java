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
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;

import lombok.val;

/**
 * {@link JWSSigner} for the {@code EdDSA} JWS algorithm (RFC 8037), backed by the
 * JDK's native EdDSA {@link Signature}. Adds no dependency and uses the same
 * {@code java.security} Ed25519 key as bundle signing. Counterpart to the
 * verifier used by {@link RealmIndexVerifier}.
 */
final class EdDsaJwsSigner implements JWSSigner {

    private static final String ALGORITHM_EDDSA = "EdDSA";

    private final PrivateKey privateKey;
    private final JCAContext jcaContext = new JCAContext();

    EdDsaJwsSigner(PrivateKey privateKey) {
        this.privateKey = privateKey;
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
    public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
        try {
            val signer = Signature.getInstance(ALGORITHM_EDDSA);
            signer.initSign(privateKey);
            signer.update(signingInput);
            return Base64URL.encode(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new JOSEException("EdDSA signing failed: " + e.getMessage(), e);
        }
    }
}
