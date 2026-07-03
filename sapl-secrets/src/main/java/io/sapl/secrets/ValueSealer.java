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

    private static final String ERROR_UNSEALABLE_VALUE    = "Refusing to seal a %s value in a secrets object.";
    private static final String ERROR_UNSEALED_NOT_SCALAR = "Refusing to unseal a %s value from a secrets leaf.";

    /** Seals a {@code secrets} object to the recipient, returning an object. */
    public static ObjectValue seal(OctetKeyPair recipientPublicKey, ObjectValue object) {
        val builder = ObjectValue.builder();
        for (val entry : object.entrySet()) {
            builder.put(entry.getKey(), seal(recipientPublicKey, entry.getValue()));
        }
        return builder.build();
    }

    /** Seals any value; objects and arrays are traversed, every scalar leaf sealed. */
    public static Value seal(OctetKeyPair recipientPublicKey, Value value) {
        return switch (value) {
        case ObjectValue object -> seal(recipientPublicKey, object);
        case ArrayValue array   -> sealArray(recipientPublicKey, array);
        default                 -> sealLeaf(recipientPublicKey, value);
        };
    }

    /** Unseals a {@code secrets} object with the recipient's private key. */
    public static ObjectValue unseal(OctetKeyPair recipientPrivateKey, ObjectValue object) {
        val builder = ObjectValue.builder();
        for (val entry : object.entrySet()) {
            builder.put(entry.getKey(), unseal(recipientPrivateKey, entry.getValue()));
        }
        return builder.build();
    }

    /** Unseals any value; sealed leaves are restored to their original scalar type, non-scalar leaves are refused. */
    public static Value unseal(OctetKeyPair recipientPrivateKey, Value value) {
        return switch (value) {
        case ObjectValue object                      -> unseal(recipientPrivateKey, object);
        case ArrayValue array                        -> unsealArray(recipientPrivateKey, array);
        case TextValue(var text) when isSealed(text) -> unsealLeaf(recipientPrivateKey, text);
        default                                      -> value;
        };
    }

    private static ArrayValue sealArray(OctetKeyPair key, ArrayValue array) {
        val elements = new ArrayList<Value>(array.size());
        for (val element : array) {
            elements.add(seal(key, element));
        }
        return Value.ofArray(elements);
    }

    private static Value sealLeaf(OctetKeyPair key, Value value) {
        if (isScalar(value)) {
            return Value.of(
                    MARKER_PREFIX + SecretSealing.seal(key, ValueJsonMarshaller.toJsonString(value)) + MARKER_SUFFIX);
        }
        throw new SecretSealingException(ERROR_UNSEALABLE_VALUE.formatted(value.getClass().getSimpleName()));
    }

    private static ArrayValue unsealArray(OctetKeyPair key, ArrayValue array) {
        val elements = new ArrayList<Value>(array.size());
        for (val element : array) {
            elements.add(unseal(key, element));
        }
        return Value.ofArray(elements);
    }

    private static Value unsealLeaf(OctetKeyPair key, String text) {
        val restored = Value.ofJson(SecretSealing.unseal(key, unwrap(text)));
        if (isScalar(restored)) {
            return restored;
        }
        throw new SecretSealingException(ERROR_UNSEALED_NOT_SCALAR.formatted(restored.getClass().getSimpleName()));
    }

    private static boolean isScalar(Value value) {
        return value instanceof TextValue || value instanceof NumberValue || value instanceof BooleanValue
                || value instanceof NullValue;
    }

    private static boolean isSealed(String text) {
        return text.startsWith(MARKER_PREFIX) && text.endsWith(MARKER_SUFFIX);
    }

    private static String unwrap(String text) {
        return text.substring(MARKER_PREFIX.length(), text.length() - MARKER_SUFFIX.length());
    }
}
