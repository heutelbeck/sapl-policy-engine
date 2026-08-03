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

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

import com.nimbusds.jose.jwk.OctetKeyPair;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Seals and unseals a {@link Value} (SOPS style): objects and arrays are
 * traversed so their structure, keys and indices stay in cleartext, and every
 * scalar leaf is replaced by a self-describing {@code ENC[...]} token wrapping
 * the leaf's JSON, so a sealed number, boolean or null round-trips back to its
 * original type, not to a string. A scalar passed directly is sealed on its own.
 * <p>
 * {@code seal}/{@code unseal} are overloaded: passing an {@link ObjectValue}
 * selects the typed variant that returns an {@code ObjectValue} (the common case,
 * a {@code secrets} object), while a {@code Value} reference uses the universal
 * variant. Value kinds that cannot legitimately appear in a configuration
 * ({@code UndefinedValue}, {@code ErrorValue}) are refused rather than passed
 * through, so nothing slips out of a sealed value unsealed.
 */
@UtilityClass
public class ValueSealer {

    private static final String MARKER_PREFIX = "ENC[";
    private static final String MARKER_SUFFIX = "]";

    private static final String ERROR_MISSING_RECIPIENT_KEY_ID = "A sealed secret leaf is malformed or declares no recipient key id.";
    private static final String ERROR_UNSEALABLE_VALUE         = "Refusing to seal a %s value in a secrets object.";
    private static final String ERROR_UNSEALED_NOT_SCALAR      = "Refusing to unseal a %s value from a secrets leaf.";

    /** Seals a {@code secrets} object to the recipient, returning an object. */
    public static ObjectValue seal(OctetKeyPair recipientPublicKey, ObjectValue object) {
        return mapLeaves(object, leaf -> sealLeaf(recipientPublicKey, leaf));
    }

    /** Seals any value; objects and arrays are traversed, every scalar leaf sealed. */
    public static Value seal(OctetKeyPair recipientPublicKey, Value value) {
        return mapLeaves(value, leaf -> sealLeaf(recipientPublicKey, leaf));
    }

    /** Unseals a {@code secrets} object with the recipient's private key. */
    public static ObjectValue unseal(OctetKeyPair recipientPrivateKey, ObjectValue object) {
        return mapLeaves(object, leaf -> unsealLeaf(recipientPrivateKey, leaf));
    }

    /** Unseals any value; sealed leaves are restored to their original scalar type, non-scalar leaves are refused. */
    public static Value unseal(OctetKeyPair recipientPrivateKey, Value value) {
        return mapLeaves(value, leaf -> unsealLeaf(recipientPrivateKey, leaf));
    }

    /**
     * Unseals a {@code secrets} object by routing each sealed leaf on its own key
     * id through {@code keyring}. A tree may legitimately hold leaves under
     * different key ids, for example after a partial reseal.
     *
     * @param keyring the keyring resolving each leaf's recipient private key
     * @param object the object to unseal
     * @return the object with every sealed leaf restored to its original scalar
     * @throws SecretSealingException if a leaf names a key id the keyring lacks, or
     * a leaf fails to unseal
     */
    public static ObjectValue unseal(Keyring keyring, ObjectValue object) {
        return mapLeaves(object, leaf -> unsealLeaf(keyring, leaf));
    }

    /**
     * Unseals any value; each sealed leaf is routed by its own key id through the
     * keyring, non-sealed leaves pass through unchanged.
     *
     * @param keyring the keyring resolving each leaf's recipient private key
     * @param value the value to unseal
     * @return the value with every sealed leaf restored to its original scalar
     * @throws SecretSealingException if a leaf names a key id the keyring lacks, or
     * a leaf fails to unseal
     */
    public static Value unseal(Keyring keyring, Value value) {
        return mapLeaves(value, leaf -> unsealLeaf(keyring, leaf));
    }

    /**
     * Reseals a {@code secrets} object to a single new recipient: every sealed leaf
     * is unsealed (routed by its own key id through {@code source}) and resealed to
     * {@code targetPublicKey}. Non-sealed leaves pass through unchanged.
     * <p>
     * This is decrypt-then-encrypt per leaf: plaintext is held transiently in
     * memory. It is not proxy re-encryption.
     *
     * @param source the keyring resolving each leaf's current recipient private key
     * @param targetPublicKey the new recipient's public key to reseal every leaf to
     * @param object the object to reseal
     * @return the object with every sealed leaf resealed to {@code targetPublicKey}
     * @throws SecretSealingException if a leaf cannot be routed, unsealed or resealed
     */
    public static ObjectValue reseal(Keyring source, OctetKeyPair targetPublicKey, ObjectValue object) {
        return mapLeaves(object, leaf -> resealLeaf(source, targetPublicKey, leaf));
    }

    /**
     * Reseals any value to a single target public key; each sealed leaf is routed
     * by its own key id, non-sealed leaves pass through unchanged.
     * <p>
     * This is decrypt-then-encrypt per leaf: plaintext is held transiently in
     * memory. It is not proxy re-encryption.
     *
     * @param source the keyring resolving each leaf's current recipient private key
     * @param targetPublicKey the new recipient's public key to reseal every leaf to
     * @param value the value to reseal
     * @return the value with every sealed leaf resealed to {@code targetPublicKey}
     * @throws SecretSealingException if a leaf cannot be routed, unsealed or resealed
     */
    public static Value reseal(Keyring source, OctetKeyPair targetPublicKey, Value value) {
        return mapLeaves(value, leaf -> resealLeaf(source, targetPublicKey, leaf));
    }

    /**
     * Returns whether a value has the sealed shape: every scalar leaf is an
     * {@code ENC[...]} token. An empty object or array trivially has the shape.
     * This is a format claim, not a cryptographic verification. A leaf that
     * merely looks like a token counts as sealed here and fails loudly at unseal
     * time. Use this to reject configurations whose secrets arrived in cleartext.
     *
     * @param value
     * the value to inspect
     *
     * @return true if every scalar leaf is an {@code ENC[...]} token
     */
    public static boolean hasSealedShape(Value value) {
        return switch (value) {
        case ObjectValue object  -> object.values().stream().allMatch(ValueSealer::hasSealedShape);
        case ArrayValue array    -> array.stream().allMatch(ValueSealer::hasSealedShape);
        case TextValue(var text) -> hasSealedShape(text);
        default                  -> false;
        };
    }

    /**
     * Reads the recipient key id from the first sealed {@code ENC[...]} leaf of a
     * value, without decrypting anything. Objects and arrays are traversed depth
     * first.
     *
     * @param value
     * the value to inspect
     *
     * @return the recipient key id named by the first sealed leaf, or empty when
     * the value carries no sealed leaf or the leaf's token names no key id
     */
    public static Optional<String> recipientKeyIdOf(Value value) {
        return switch (value) {
        case ObjectValue object                            ->
            object.values().stream().map(ValueSealer::recipientKeyIdOf).flatMap(Optional::stream).findFirst();
        case ArrayValue array                              ->
            array.stream().map(ValueSealer::recipientKeyIdOf).flatMap(Optional::stream).findFirst();
        case TextValue(var text) when hasSealedShape(text) -> SecretSealing.recipientKeyIdOf(unwrap(text));
        default                                            -> Optional.empty();
        };
    }

    /**
     * Reads every recipient key id from the sealed leaves of a value without
     * decrypting it.
     *
     * @param value the value to inspect
     * @return the distinct recipient key ids declared by its sealed leaves
     * @throws SecretSealingException if a sealed-looking leaf is malformed or
     * declares no key id
     */
    public static Set<String> recipientKeyIdsOf(Value value) {
        val recipients = new TreeSet<String>();
        collectRecipientKeyIds(value, recipients);
        return Set.copyOf(recipients);
    }

    private static ObjectValue mapLeaves(ObjectValue object, UnaryOperator<Value> leafOperation) {
        val builder = ObjectValue.builder();
        for (val entry : object.entrySet()) {
            builder.put(entry.getKey(), mapLeaves(entry.getValue(), leafOperation));
        }
        return builder.build();
    }

    private static Value mapLeaves(Value value, UnaryOperator<Value> leafOperation) {
        return switch (value) {
        case ObjectValue object -> mapLeaves(object, leafOperation);
        case ArrayValue array   -> {
            val elements = new ArrayList<Value>(array.size());
            for (val element : array) {
                elements.add(mapLeaves(element, leafOperation));
            }
            yield Value.ofArray(elements);
        }
        default                 -> leafOperation.apply(value);
        };
    }

    private static Value sealLeaf(OctetKeyPair key, Value leaf) {
        if (isScalar(leaf)) {
            return Value.of(
                    MARKER_PREFIX + SecretSealing.seal(key, ValueJsonMarshaller.toJsonString(leaf)) + MARKER_SUFFIX);
        }
        throw new SecretSealingException(ERROR_UNSEALABLE_VALUE.formatted(leaf.getClass().getSimpleName()));
    }

    private static Value unsealLeaf(OctetKeyPair key, Value leaf) {
        if (leaf instanceof TextValue(var text) && hasSealedShape(text)) {
            return restoreLeaf(SecretSealing.unseal(key, unwrap(text)));
        }
        return leaf;
    }

    private static Value unsealLeaf(Keyring keyring, Value leaf) {
        if (leaf instanceof TextValue(var text) && hasSealedShape(text)) {
            return restoreLeaf(SecretSealing.unseal(keyring, unwrap(text)));
        }
        return leaf;
    }

    private static Value resealLeaf(Keyring source, OctetKeyPair targetPublicKey, Value leaf) {
        if (leaf instanceof TextValue(var text) && hasSealedShape(text)) {
            return Value
                    .of(MARKER_PREFIX + SecretSealing.reseal(source, targetPublicKey, unwrap(text)) + MARKER_SUFFIX);
        }
        return leaf;
    }

    private static void collectRecipientKeyIds(Value value, Set<String> recipients) {
        switch (value) {
        case ObjectValue object  -> object.values().forEach(child -> collectRecipientKeyIds(child, recipients));
        case ArrayValue array    -> array.forEach(child -> collectRecipientKeyIds(child, recipients));
        case TextValue(var text) -> {
            if (hasSealedShape(text)) {
                recipients.add(SecretSealing.recipientKeyIdOf(unwrap(text))
                        .orElseThrow(() -> new SecretSealingException(ERROR_MISSING_RECIPIENT_KEY_ID)));
            }
        }
        default                  -> {
            // Non-sealed leaves have no recipient.
        }
        }
    }

    private static Value restoreLeaf(String plaintext) {
        val restored = Value.ofJson(plaintext);
        if (isScalar(restored)) {
            return restored;
        }
        throw new SecretSealingException(ERROR_UNSEALED_NOT_SCALAR.formatted(restored.getClass().getSimpleName()));
    }

    private static boolean isScalar(Value value) {
        return value instanceof TextValue || value instanceof NumberValue || value instanceof BooleanValue
                || value instanceof NullValue;
    }

    private static boolean hasSealedShape(String text) {
        return text.startsWith(MARKER_PREFIX) && text.endsWith(MARKER_SUFFIX);
    }

    private static String unwrap(String text) {
        return text.substring(MARKER_PREFIX.length(), text.length() - MARKER_SUFFIX.length());
    }
}
