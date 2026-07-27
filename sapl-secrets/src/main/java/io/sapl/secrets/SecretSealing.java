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
import java.util.Optional;
import java.util.UUID;

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

    private static final String ERROR_CANNOT_GENERATE_KEY    = "Cannot generate a recipient key.";
    private static final String ERROR_CANNOT_SEAL            = "Cannot seal a secret.";
    private static final String ERROR_CANNOT_UNSEAL          = "Cannot unseal a secret.";
    private static final String ERROR_MISSING_KEY_ID         = "Cannot unseal a secret: the sealed token declares no key id to route by.";
    private static final String ERROR_UNEXPECTED_ALGORITHM   = "Refusing to unseal: expected %s/%s but the token declares %s/%s.";
    private static final String ERROR_UNEXPECTED_COMPRESSION = "Refusing to unseal: the token declares compression %s but none is expected.";
    private static final String ERROR_UNKNOWN_KEY_ID         = "Cannot unseal a secret: the keyring holds no key for key id '%s'.";

    /**
     * Generates a recipient key pair with a fresh, unique key id (the private key;
     * its public part seals). Two calls never collide on key id, so generated keys
     * route unambiguously through a {@link Keyring}.
     *
     * @return a new X25519 recipient key pair with a random key id
     * @throws SecretSealingException if key generation fails
     */
    public static OctetKeyPair generateRecipientKey() {
        return generateRecipientKey(UUID.randomUUID().toString());
    }

    /**
     * Generates a recipient key pair with a caller-chosen key id (the private key;
     * its public part seals). Use this for a stable, addressable key id; use
     * {@link #generateRecipientKey()} when any unique id will do.
     *
     * @param keyId the key id to stamp on the generated key
     * @return a new X25519 recipient key pair carrying {@code keyId}
     * @throws SecretSealingException if key generation fails
     */
    public static OctetKeyPair generateRecipientKey(String keyId) {
        try {
            return new OctetKeyPairGenerator(CURVE).keyID(keyId).generate();
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

    /**
     * Reads the recipient key id from a compact JWE token produced by
     * {@link #seal}, without decrypting it.
     *
     * @param sealedCompactJwe the compact JWE token
     * @return the recipient key id from the token header, or empty when the token
     * cannot be parsed or names no key id
     */
    public static Optional<String> recipientKeyIdOf(String sealedCompactJwe) {
        if (sealedCompactJwe == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JWEObject.parse(sealedCompactJwe).getHeader().getKeyID());
        } catch (ParseException e) {
            return Optional.empty();
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

    /**
     * Unseals a token produced by {@link #seal} by routing on the token's own key
     * id: reads the recipient key id from the token, resolves the matching private
     * key from {@code keyring}, and unseals. Fails closed.
     *
     * @param keyring the keyring to resolve the recipient private key from
     * @param sealed the compact JWE token to unseal
     * @return the recovered plaintext
     * @throws SecretSealingException if the token declares no key id, if the
     * keyring holds no key for that key id, or if unsealing fails
     */
    public static String unseal(Keyring keyring, String sealed) {
        val keyId      = recipientKeyIdOf(sealed).orElseThrow(() -> new SecretSealingException(ERROR_MISSING_KEY_ID));
        val privateKey = keyring.privateKeyFor(keyId)
                .orElseThrow(() -> new SecretSealingException(ERROR_UNKNOWN_KEY_ID.formatted(keyId)));
        return unseal(privateKey, sealed);
    }

    /**
     * Reseals a token to a new recipient: unseals it with the key the
     * {@code source} keyring resolves for the token's key id, then seals the
     * recovered plaintext to {@code targetPublicKey}. Equivalent to
     * {@code seal(targetPublicKey, unseal(source, sealed))}.
     * <p>
     * This is decrypt-then-encrypt: the caller holds the plaintext transiently in
     * memory between the two steps. It is not proxy re-encryption.
     *
     * @param source the keyring resolving the current recipient's private key
     * @param targetPublicKey the new recipient's public key to seal to
     * @param sealed the compact JWE token to reseal
     * @return a new compact JWE token sealed to {@code targetPublicKey}
     * @throws SecretSealingException if the source token cannot be routed or
     * unsealed, or if sealing to the target fails
     */
    public static String reseal(Keyring source, OctetKeyPair targetPublicKey, String sealed) {
        return seal(targetPublicKey, unseal(source, sealed));
    }
}
