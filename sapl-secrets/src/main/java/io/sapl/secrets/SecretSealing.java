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

import java.text.ParseException;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.X25519Decrypter;
import com.nimbusds.jose.crypto.X25519Encrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Seals and unseals a single secret value with JOSE/JWE, using the strongest
 * interoperable settings, pinned so the decrypter never trusts the token's
 * header:
 * <ul>
 * <li>key management: <b>ECDH-ES</b> over the <b>X25519</b> OKP curve,</li>
 * <li>content encryption: <b>A256GCM</b> (AES-256, AEAD).</li>
 * </ul>
 * The recipient key is a parameter: a public key seals, the matching private key
 * unseals. The algorithm and encryption method are fixed and re-checked on
 * unseal, closing the classic JWE algorithm-substitution weakness.
 */
@UtilityClass
public class SecretSealing {

    private static final JWEAlgorithm     ALGORITHM  = JWEAlgorithm.ECDH_ES;
    private static final EncryptionMethod ENCRYPTION = EncryptionMethod.A256GCM;
    private static final Curve            CURVE      = Curve.X25519;
    private static final String           KEY_ID     = "recipient";

    private static final String ERROR_CANNOT_GENERATE_KEY    = "Cannot generate a recipient key.";
    private static final String ERROR_CANNOT_SEAL            = "Cannot seal a secret.";
    private static final String ERROR_CANNOT_UNSEAL          = "Cannot unseal a secret.";
    private static final String ERROR_UNEXPECTED_ALGORITHM   = "Refusing to unseal: expected %s/%s but the token declares %s/%s.";
    private static final String ERROR_UNEXPECTED_COMPRESSION = "Refusing to unseal: the token declares compression %s but none is expected.";

    /** Generates a recipient key pair (the private key; its public part seals). */
    public static OctetKeyPair generateRecipientKey() {
        try {
            return new OctetKeyPairGenerator(CURVE).keyID(KEY_ID).generate();
        } catch (JOSEException e) {
            throw new SecretSealingException(ERROR_CANNOT_GENERATE_KEY, e);
        }
    }

    /** Seals the plaintext to the recipient, returning a compact JWE token. */
    public static String seal(OctetKeyPair recipientPublicKey, String plaintext) {
        try {
            val header = new JWEHeader.Builder(ALGORITHM, ENCRYPTION).keyID(recipientPublicKey.getKeyID()).build();
            val jwe    = new JWEObject(header, new Payload(plaintext));
            jwe.encrypt(new X25519Encrypter(recipientPublicKey.toPublicJWK()));
            return jwe.serialize();
        } catch (JOSEException e) {
            throw new SecretSealingException(ERROR_CANNOT_SEAL, e);
        }
    }

    /** Unseals a token produced by {@link #seal}, using the recipient's private key. */
    public static String unseal(OctetKeyPair recipientPrivateKey, String sealed) {
        try {
            val jwe    = JWEObject.parse(sealed);
            val header = jwe.getHeader();
            if (!ALGORITHM.equals(header.getAlgorithm()) || !ENCRYPTION.equals(header.getEncryptionMethod())) {
                throw new SecretSealingException(ERROR_UNEXPECTED_ALGORITHM.formatted(ALGORITHM, ENCRYPTION,
                        header.getAlgorithm(), header.getEncryptionMethod()));
            }
            if (header.getCompressionAlgorithm() != null) {
                throw new SecretSealingException(
                        ERROR_UNEXPECTED_COMPRESSION.formatted(header.getCompressionAlgorithm()));
            }
            jwe.decrypt(new X25519Decrypter(recipientPrivateKey));
            return jwe.getPayload().toString();
        } catch (ParseException | JOSEException e) {
            throw new SecretSealingException(ERROR_CANNOT_UNSEAL, e);
        }
    }
}
