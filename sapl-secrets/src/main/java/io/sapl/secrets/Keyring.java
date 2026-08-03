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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;

import lombok.val;

/**
 * An immutable holder of private {@link OctetKeyPair}s indexed by their key id.
 * Callers resolve a private key by the key id a sealed token names, which is
 * what lets a single unseal or reseal route across keys of different ids. This
 * is a neutral key lookup, with no knowledge of where the keys come from or what
 * they protect.
 *
 * @param privateKeysByKeyId the private keys, keyed by their key id; copied on
 * construction so the keyring stays immutable
 */
public record Keyring(Map<String, OctetKeyPair> privateKeysByKeyId) {

    private static final String ERROR_DUPLICATE_KEY_ID = "Cannot build a keyring: two keys share the key id '%s'.";
    private static final String ERROR_DUPLICATE_KEY_MATERIAL = "Cannot build a keyring: the same X25519 private key is configured more than once.";
    private static final String ERROR_INCONSISTENT_KEY_ID = "Cannot build a keyring: the key indexed under '%s' declares key id '%s'.";
    private static final String ERROR_KEY_MUST_BE_PRIVATE = "Cannot build a keyring: every key must contain private key material.";
    private static final String ERROR_KEY_MUST_BE_X25519 = "Cannot build a keyring: every key must use the X25519 curve.";
    private static final String ERROR_MISSING_KEY_ID = "Cannot build a keyring: a key names no key id or a blank key id.";

    /**
     * Canonical constructor; validates that every key is indexed under its own
     * key id, then defensively copies the map so the keyring is immutable and
     * self-consistent however it is built, not only through {@link #of}.
     *
     * @throws NullPointerException if the map or a key is null
     * @throws SecretSealingException if a key has no id, uses another curve, has
     * no private material, is indexed under another id, or duplicates key material
     */
    public Keyring {
        Objects.requireNonNull(privateKeysByKeyId, "privateKeysByKeyId");
        val publicCoordinates = new HashSet<String>();
        for (val entry : privateKeysByKeyId.entrySet()) {
            val key         = Objects.requireNonNull(entry.getValue(), "privateKeysByKeyId contains a null key");
            val actualKeyId = entry.getValue().getKeyID();
            if (actualKeyId == null || actualKeyId.isBlank()) {
                throw new SecretSealingException(ERROR_MISSING_KEY_ID);
            }
            if (!Curve.X25519.equals(key.getCurve())) {
                throw new SecretSealingException(ERROR_KEY_MUST_BE_X25519);
            }
            if (!key.isPrivate()) {
                throw new SecretSealingException(ERROR_KEY_MUST_BE_PRIVATE);
            }
            if (!actualKeyId.equals(entry.getKey())) {
                throw new SecretSealingException(ERROR_INCONSISTENT_KEY_ID.formatted(entry.getKey(), actualKeyId));
            }
            if (!publicCoordinates.add(key.getX().toString())) {
                throw new SecretSealingException(ERROR_DUPLICATE_KEY_MATERIAL);
            }
        }
        privateKeysByKeyId = Map.copyOf(privateKeysByKeyId);
    }

    /**
     * Builds a keyring indexing each key by its own {@code getKeyID()}.
     *
     * @param privateKeys the private keys to index
     * @return a keyring resolving each key by its key id
     * @throws NullPointerException if the array or a key is null
     * @throws SecretSealingException if a key is invalid, or keys share an id or
     * key material
     */
    public static Keyring of(OctetKeyPair... privateKeys) {
        Objects.requireNonNull(privateKeys, "privateKeys");
        val index = new LinkedHashMap<String, OctetKeyPair>();
        for (val key : privateKeys) {
            Objects.requireNonNull(key, "privateKeys contains a null key");
            if (index.putIfAbsent(key.getKeyID(), key) != null) {
                throw new SecretSealingException(ERROR_DUPLICATE_KEY_ID.formatted(key.getKeyID()));
            }
        }
        return new Keyring(index);
    }

    /**
     * Builds a keyring indexing each key by its own {@code getKeyID()}.
     *
     * @param privateKeys the private keys to index
     * @return a keyring resolving each key by its key id
     * @throws NullPointerException if the collection or a key is null
     * @throws SecretSealingException if a key is invalid, or keys share an id or
     * key material
     */
    public static Keyring of(Collection<OctetKeyPair> privateKeys) {
        Objects.requireNonNull(privateKeys, "privateKeys");
        return of(privateKeys.toArray(OctetKeyPair[]::new));
    }

    /**
     * Resolves the private key for a key id.
     *
     * @param keyId the key id to resolve
     * @return the matching private key, or empty when the keyring holds none
     */
    public Optional<OctetKeyPair> privateKeyFor(String keyId) {
        return Optional.ofNullable(privateKeysByKeyId.get(keyId));
    }

    /**
     * Returns a diagnostic representation containing key ids but no key material.
     *
     * @return the safe diagnostic representation
     */
    @Override
    public String toString() {
        return "Keyring[keyIds=" + privateKeysByKeyId.keySet() + "]";
    }
}
