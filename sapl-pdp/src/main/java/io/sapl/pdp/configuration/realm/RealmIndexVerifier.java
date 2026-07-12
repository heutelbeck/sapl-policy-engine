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

import java.security.PublicKey;
import java.text.ParseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;

import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.bundle.BundleSignatureException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Verifies a compact-JWS realm index and returns its payload, mirroring the
 * algorithm-pinning discipline of {@code BundleSigner}: the algorithm is fixed to
 * {@code EdDSA} (any other value, including {@code none}, is refused), the key is
 * resolved through the same {@link BundleSecurityPolicy} trusted-key path as
 * bundles, and the index is additionally checked for the expected realm and a
 * strictly newer sequence (anti-rollback). When the policy does not require
 * signatures (development), the signature check is skipped but the realm and
 * sequence checks still apply.
 */
@UtilityClass
public class RealmIndexVerifier {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String ERROR_MALFORMED_INDEX      = "Realm index is not a valid JWS.";
    private static final String ERROR_MALFORMED_PAYLOAD    = "Realm index payload is not a valid index document.";
    private static final String ERROR_SIGNATURE_INVALID    = "Realm index signature verification failed.";
    private static final String ERROR_STALE_SEQUENCE       = "Refusing realm index with sequence %d; not newer than the last accepted %d.";
    private static final String ERROR_UNEXPECTED_ALGORITHM = "Refusing realm index: expected EdDSA but the token declares %s.";
    private static final String ERROR_UNEXPECTED_TYPE      = "Refusing realm index: expected type %s but the token declares %s.";
    private static final String ERROR_UNRESOLVED_KEY       = "Realm index signing key could not be resolved: %s";
    private static final String ERROR_WRONG_REALM          = "Realm index is for realm '%s' but '%s' was expected.";

    /**
     * Verifies a realm index and returns it.
     *
     * @param compactJws the compact-JWS realm index
     * @param securityPolicy the policy resolving the trusted signing key
     * @param expectedRealm the realm the consumer expects
     * @param lastAcceptedSequence the sequence of the last accepted index; the new
     * one must be strictly greater
     *
     * @return the verified realm index
     *
     * @throws RealmIndexException if the index is malformed, its signature is
     * invalid (when required), it is for the wrong realm, or its sequence is not
     * newer
     */
    public static RealmIndex verify(String compactJws, BundleSecurityPolicy securityPolicy, String expectedRealm,
            long lastAcceptedSequence) {
        val jws = parse(compactJws);
        if (securityPolicy.signatureRequired()) {
            requireHeader(jws.getHeader());
            final PublicKey publicKey;
            try {
                publicKey = securityPolicy.resolvePublicKey(expectedRealm, jws.getHeader().getKeyID());
            } catch (BundleSignatureException e) {
                // Honour the documented contract: an unresolved or untrusted key is a
                // rejected index, not a leaking exception type that callers catching
                // RealmIndexException would miss.
                throw new RealmIndexException(ERROR_UNRESOLVED_KEY.formatted(e.getMessage()), e);
            }
            verifySignature(jws, publicKey);
        }
        val index = readIndex(jws);
        requireRealm(index, expectedRealm);
        requireNewerSequence(index, lastAcceptedSequence);
        return index;
    }

    private static JWSObject parse(String compactJws) {
        try {
            return JWSObject.parse(compactJws);
        } catch (ParseException e) {
            throw new RealmIndexException(ERROR_MALFORMED_INDEX, e);
        }
    }

    private static void requireHeader(JWSHeader header) {
        if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm())) {
            throw new RealmIndexException(ERROR_UNEXPECTED_ALGORITHM.formatted(header.getAlgorithm()));
        }
        val type = header.getType();
        if (type == null || !RealmIndexSigner.TYPE.equals(type.getType())) {
            throw new RealmIndexException(ERROR_UNEXPECTED_TYPE.formatted(RealmIndexSigner.TYPE, type));
        }
    }

    private static void verifySignature(JWSObject jws, PublicKey publicKey) {
        try {
            if (!jws.verify(new EdDsaJwsVerifier(publicKey))) {
                throw new RealmIndexException(ERROR_SIGNATURE_INVALID);
            }
        } catch (JOSEException e) {
            throw new RealmIndexException(ERROR_SIGNATURE_INVALID, e);
        }
    }

    private static RealmIndex readIndex(JWSObject jws) {
        try {
            return MAPPER.readValue(jws.getPayload().toString(), RealmIndex.class);
        } catch (JacksonException e) {
            throw new RealmIndexException(ERROR_MALFORMED_PAYLOAD, e);
        }
    }

    private static void requireRealm(RealmIndex index, String expectedRealm) {
        if (!expectedRealm.equals(index.realm())) {
            throw new RealmIndexException(ERROR_WRONG_REALM.formatted(index.realm(), expectedRealm));
        }
    }

    private static void requireNewerSequence(RealmIndex index, long lastAcceptedSequence) {
        if (index.sequence() <= lastAcceptedSequence) {
            throw new RealmIndexException(ERROR_STALE_SEQUENCE.formatted(index.sequence(), lastAcceptedSequence));
        }
    }
}
