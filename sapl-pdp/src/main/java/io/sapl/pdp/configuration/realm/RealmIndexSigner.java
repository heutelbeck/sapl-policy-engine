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

import java.security.PrivateKey;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;

import tools.jackson.databind.json.JsonMapper;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Signs a {@link RealmIndex} as a compact JWS with {@code EdDSA} over the JDK's
 * native Ed25519, using the same {@code java.security} key as bundle signing.
 * The counterpart verifier is {@link RealmIndexVerifier}. This is an open
 * primitive (like {@code BundleSigner}); a bundle server produces indexes with
 * it, and the reference client verifies them.
 */
@UtilityClass
public class RealmIndexSigner {

    /** The JWS {@code typ} header value identifying a realm index. */
    static final String TYPE = "sapl-realm-index";

    private static final JOSEObjectType INDEX_TYPE = new JOSEObjectType(TYPE);
    private static final JsonMapper     MAPPER     = JsonMapper.builder().build();

    private static final String ERROR_CANNOT_SIGN = "Cannot sign the realm index.";

    /**
     * Signs a realm index as a compact JWS.
     *
     * @param index the realm index to sign
     * @param signingKey the Ed25519 private key
     * @param keyId the key identifier placed in the JWS {@code kid} header
     *
     * @return the compact JWS serialization
     *
     * @throws RealmIndexException if signing fails
     */
    public static String sign(RealmIndex index, PrivateKey signingKey, String keyId) {
        try {
            val header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(keyId).type(INDEX_TYPE).build();
            val jws    = new JWSObject(header, new Payload(MAPPER.writeValueAsString(index)));
            jws.sign(new EdDsaJwsSigner(signingKey));
            return jws.serialize();
        } catch (JOSEException e) {
            throw new RealmIndexException(ERROR_CANNOT_SIGN, e);
        }
    }
}
