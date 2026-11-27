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
package io.sapl.api.binary;

import static io.sapl.api.binary.SaplBinaryFormatException.Reason.TOO_DEEP;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.UNPAIRED_SURROGATE;

import java.math.BigDecimal;
import java.util.Arrays;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.val;

/**
 * Writes the wire objects of one payload in the SAPL binary format: the
 * structure and the texts into buffers of their own, joined behind the lead
 * byte at the end. Arrays and objects nested deeper than 500 are refused.
 */
final class BinaryWriter {

    /** The room made for the structure and for the texts at first. */
    private static final int INITIAL_STRUCTURE_CAPACITY = 128;
    private static final int INITIAL_REGION_CAPACITY    = 256;

    /** The most bytes of the lead byte and of the varint of the region length. */
    private static final int MAX_HEAD_LENGTH = 11;

    private static final long[] POWERS_OF_TEN = new long[19];

    static {
        var power = 1L;
        for (var i = 0; i < POWERS_OF_TEN.length; i++) {
            POWERS_OF_TEN[i]  = power;
            power            *= 10;
        }
    }

    private final ByteSink structure = new ByteSink(INITIAL_STRUCTURE_CAPACITY);
    private final ByteSink region    = new ByteSink(INITIAL_REGION_CAPACITY);

    /** The payload: the lead byte, the length of the text region, the region, the structure. */
    byte[] toBytes() {
        val payload = new ByteSink(MAX_HEAD_LENGTH + region.size + structure.size);
        payload.put(SaplBinaryCodec.LEAD_BYTE);
        payload.varint(region.size);
        payload.put(region.bytes, 0, region.size);
        payload.put(structure.bytes, 0, structure.size);
        return payload.toArray();
    }

    /** A subscription: the four parts, an undefined environment included, and the secrets. */
    void subscription(AuthorizationSubscription subscription) throws SaplBinaryFormatException {
        value(subscription.subject(), 0);
        value(subscription.action(), 0);
        value(subscription.resource(), 0);
        value(subscription.environment(), 0);
        object(subscription.secrets(), 0);
    }

    /** A decision: the verdict, the obligations, the advice and the resource. */
    void decision(AuthorizationDecision decision) throws SaplBinaryFormatException {
        structure.put(verdict(decision.decision()));
        for (val values : new ArrayValue[] { decision.obligations(), decision.advice() }) {
            count(values.size());
            for (val value : values) {
                value(value, 0);
            }
        }
        value(decision.resource(), 0);
    }

    /** A text that carries no tag, such as a key or an id: its length, its characters to the region. */
    void text(String text) throws SaplBinaryFormatException {
        count(regionText(text));
    }

    /** A count or a length. */
    void count(int count) {
        structure.varint(count);
    }

    private static int verdict(Decision decision) {
        return switch (decision) {
        case INDETERMINATE  -> 0;
        case PERMIT         -> 1;
        case DENY           -> 2;
        case NOT_APPLICABLE -> 3;
        case SUSPEND        -> 4;
        };
    }

    /** A value inside {@code enclosing} arrays and objects. */
    private void value(Value value, int enclosing) throws SaplBinaryFormatException {
        switch (value) {
        case TextValue(var text)     -> head(Tag.SHORT_TEXT, Tag.TEXT, regionText(text));
        case NumberValue(var number) -> number(number);
        case ObjectValue object      -> object(object, enclosing);
        case ArrayValue array        -> array(array, enclosing);
        case BooleanValue(var flag)  -> structure.put(flag ? Tag.TRUE : Tag.FALSE);
        case NullValue ignored       -> structure.put(Tag.NULL);
        case UndefinedValue ignored  -> structure.put(Tag.UNDEFINED);
        case ErrorValue error        -> {
            structure.put(Tag.ERROR);
            text(error.message() == null ? "" : error.message());
        }
        }
    }

    private void array(ArrayValue array, int enclosing) throws SaplBinaryFormatException {
        checkDepth(enclosing);
        head(Tag.SHORT_ARRAY, Tag.ARRAY, array.size());
        for (val element : array) {
            value(element, enclosing + 1);
        }
    }

    private void object(ObjectValue object, int enclosing) throws SaplBinaryFormatException {
        checkDepth(enclosing);
        head(Tag.SHORT_OBJECT, Tag.OBJECT, object.size());
        for (val entry : object.entrySet()) {
            text(entry.getKey());
            value(entry.getValue(), enclosing + 1);
        }
    }

    private static void checkDepth(int enclosing) throws SaplBinaryFormatException {
        if (enclosing >= SaplBinaryCodec.MAX_DEPTH) {
            throw new SaplBinaryFormatException(TOO_DEEP);
        }
    }

    /** Appends a text to the region and returns its length in bytes. */
    private int regionText(String text) throws SaplBinaryFormatException {
        region.ensure(text.length() * 3);
        val start = region.size;
        val stop  = Utf8.encode(text, region.bytes, start);
        if (stop < 0) {
            throw new SaplBinaryFormatException(UNPAIRED_SURROGATE);
        }
        region.size = stop;
        return stop - start;
    }

    /**
     * The tag of a text, array or object with its length: the short tag if the
     * length fits into it, else the long tag and the length.
     */
    private void head(int shortTag, int longTag, int length) {
        if (length <= Tag.MAX_SHORT_LENGTH) {
            structure.put(shortTag | length);
        } else {
            structure.put(longTag);
            count(length);
        }
    }

    /**
     * A whole number within 64 bits as an integer, any other as its unscaled
     * value and scale, as it is held: {@code 2.50} is 250 with the scale 2.
     */
    private void number(BigDecimal number) {
        if (isWholeLong(number)) {
            val integer = number.longValue();
            if (integer >= 0 && integer < Tag.SMALL_INTEGER) {
                structure.put(Tag.SMALL_INTEGER | (int) integer);
            } else {
                structure.put(Tag.INTEGER);
                structure.varint(zigzag(integer));
            }
            return;
        }
        if (fitsLong(number)) {
            structure.put(Tag.DECIMAL);
            structure.varint(zigzag(number.unscaledValue().longValue()));
            structure.varint(zigzag(number.scale()));
            return;
        }
        val unscaled = number.unscaledValue().toByteArray();
        structure.put(Tag.BIG_DECIMAL);
        structure.varint(zigzag(number.scale()));
        count(unscaled.length);
        structure.put(unscaled, 0, unscaled.length);
    }

    /** Whether the number is whole and within a signed 64-bit integer, whatever notation it is held in. */
    private static boolean isWholeLong(BigDecimal number) {
        val scale = number.scale();
        if (scale == 0) {
            return fitsLong(number);
        }
        if (number.signum() == 0) {
            return true;
        }
        if (scale < 0) {
            val digits = (long) number.precision() - scale;
            return digits <= 18 || (digits == 19 && number.toBigInteger().bitLength() < 64);
        }
        if (number.precision() <= 18) {
            // A nonzero unscaled value below 10^18 is not a multiple of a larger power of ten.
            return scale <= 18 && number.unscaledValue().longValue() % POWERS_OF_TEN[scale] == 0;
        }
        val stripped = number.stripTrailingZeros();
        return stripped.scale() <= 0 && isWholeLong(stripped);
    }

    /** Whether the unscaled value fits a signed 64-bit integer. */
    private static boolean fitsLong(BigDecimal number) {
        return number.precision() <= 18 || number.unscaledValue().bitLength() < 64;
    }

    /** The zigzag form of a signed number, which keeps numbers near zero short as varints. */
    private static long zigzag(long number) {
        return (number << 1) ^ (number >> 63);
    }

    /** A growable byte buffer. */
    private static final class ByteSink {

        private byte[] bytes;
        private int    size;

        ByteSink(int capacity) {
            bytes = new byte[capacity];
        }

        /** Makes room for {@code extra} more bytes. */
        void ensure(int extra) {
            val needed = size + extra;
            if (needed > bytes.length) {
                bytes = Arrays.copyOf(bytes, Math.max(needed, bytes.length << 1));
            }
        }

        void put(int b) {
            ensure(1);
            bytes[size++] = (byte) b;
        }

        void put(byte[] source, int offset, int length) {
            ensure(length);
            System.arraycopy(source, offset, bytes, size, length);
            size += length;
        }

        /** An unsigned varint: seven bits per byte, the least significant first, at most ten bytes. */
        void varint(long value) {
            ensure(10);
            var rest = value;
            while ((rest & ~0x7FL) != 0) {
                bytes[size++]    = (byte) ((rest & 0x7F) | 0x80);
                rest          >>>= 7;
            }
            bytes[size++] = (byte) rest;
        }

        byte[] toArray() {
            return size == bytes.length ? bytes : Arrays.copyOf(bytes, size);
        }
    }
}
