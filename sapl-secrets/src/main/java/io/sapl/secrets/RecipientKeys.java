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
package io.sapl.secrets;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;

import javax.crypto.KeyAgreement;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Checks of recipient keys as they are loaded, before any secret is sealed to
 * them or unsealed with them.
 */
@UtilityClass
public class RecipientKeys {

    private static final Curve      CURVE             = Curve.X25519;
    private static final String     KEY_AGREEMENT     = "XDH";
    private static final BigInteger X25519_BASE_POINT = BigInteger.valueOf(9);

    private static final String ERROR_MISMATCHED_KEY = "The public part of the key does not belong to its private part.";

    /**
     * Checks a recipient key when it is loaded: the public part of a private key
     * must belong to its private part. Unsealing uses only the private part, so a
     * key whose public part was replaced would otherwise load and fail only at
     * the first token sealed to that public part. A key without a private part,
     * or on another curve than X25519, has nothing to compare and passes
     * unchanged; its use refuses it.
     *
     * @param recipientKey the recipient key to check
     * @return {@code recipientKey}
     * @throws SecretSealingException if the public part does not belong to the
     * private part
     */
    public static OctetKeyPair requireMatchingPublicPart(OctetKeyPair recipientKey) {
        if (recipientKey.isPrivate() && CURVE.equals(recipientKey.getCurve())
                && !isPublicPartOfPrivatePart(recipientKey)) {
            throw new SecretSealingException(ERROR_MISMATCHED_KEY);
        }
        return recipientKey;
    }

    // The public key of an X25519 private key is the agreement of the private key
    // with the base point (RFC 7748, section 6.1).
    private static boolean isPublicPartOfPrivatePart(OctetKeyPair recipientKey) {
        try {
            val keyFactory = KeyFactory.getInstance(KEY_AGREEMENT);
            val privateKey = keyFactory
                    .generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, recipientKey.getDecodedD()));
            val basePoint  = keyFactory
                    .generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, X25519_BASE_POINT));
            val agreement  = KeyAgreement.getInstance(KEY_AGREEMENT);
            agreement.init(privateKey);
            agreement.doPhase(basePoint, true);
            return MessageDigest.isEqual(agreement.generateSecret(), recipientKey.getDecodedX());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
