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

import static io.sapl.api.binary.SaplBinaryFormatException.Reason.INVALID_NUMBER;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.MALFORMED;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.SECRETS_NOT_AN_OBJECT;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.TOO_DEEP;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValueLimits;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.val;

/**
 * Reads the wire objects of one payload in the SAPL binary format: the lead
 * byte, the text region, which is checked to be UTF-8 once, and the structure
 * behind it.
 * <p>
 * Arrays and objects are frames on stacks of their own, not on the call stack,
 * so the depth of a value costs no stack. Array elements collect on a scratch
 * stack that the whole payload shares; object entries go into the builder of
 * their frame. The stacks are made with the first array or object, as most
 * decisions have none.
 */
final class BinaryReader {

    /** The most bytes of the unscaled value of a big decimal: enough for 1000 decimal digits. */
    private static final int MAX_BIG_DECIMAL_BYTES = 418;

    private static final Decision[] VERDICTS = { Decision.INDETERMINATE, Decision.PERMIT, Decision.DENY,
            Decision.NOT_APPLICABLE, Decision.SUSPEND };

    private final byte[] payload;
    private final int    regionEnd;
    private int          position;
    private int          regionPosition;

    private int[]                 frameRemaining;
    private int[]                 frameStart;
    private ObjectValue.Builder[] frameBuilder;
    private String[]              frameKey;
    private int                   depth;
    private Value[]               scratch;
    private int                   scratchSize;

    /**
     * A reader at the start of the structure, behind the lead byte and the text
     * region.
     */
    BinaryReader(byte[] payload) throws SaplBinaryFormatException {
        this.payload = payload;
        if (nextByte() != SaplBinaryCodec.LEAD_BYTE) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        val regionLength = length();
        if (regionLength > payload.length - position || !Utf8.isValid(payload, position, regionLength)) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        regionPosition = position;
        regionEnd      = position + regionLength;
        position       = regionEnd;
    }

    /** A subscription: subject, action, resource, environment, and the secrets, which must be an object. */
    AuthorizationSubscription subscription() throws SaplBinaryFormatException {
        val subject     = value();
        val action      = value();
        val resource    = value();
        val environment = value();
        return new AuthorizationSubscription(subject, action, resource, environment, secrets());
    }

    /** A decision: the verdict, the obligations, the advice and the resource. */
    AuthorizationDecision decision() throws SaplBinaryFormatException {
        val verdict     = nextByte();
        val decision    = verdict < VERDICTS.length ? VERDICTS[verdict] : Decision.INDETERMINATE;
        val obligations = values();
        val advice      = values();
        return new AuthorizationDecision(decision, obligations, advice, value());
    }

    /** A text that carries no tag, such as a key or an id: its length, its characters in the region. */
    String text() throws SaplBinaryFormatException {
        return regionText(length());
    }

    /** A length or a count. Anything beyond an int is beyond any payload. */
    int length() throws SaplBinaryFormatException {
        val length = varint();
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        return (int) length;
    }

    /** Nothing may follow the structure, and every text of the region must have been named. */
    void end() throws SaplBinaryFormatException {
        if (position != payload.length || regionPosition != regionEnd) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
    }

    private int nextByte() throws SaplBinaryFormatException {
        if (position >= payload.length) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        return payload[position++] & 0xFF;
    }

    /**
     * An unsigned varint: seven bits per byte, the least significant first, at
     * most ten bytes, of which the tenth may hold the 64th bit only.
     */
    private long varint() throws SaplBinaryFormatException {
        var number = 0L;
        for (var shift = 0; shift < 64; shift += 7) {
            val b = nextByte();
            if (shift == 63 && b > 1) {
                throw new SaplBinaryFormatException(MALFORMED);
            }
            number |= (long) (b & 0x7F) << shift;
            if (b < 0x80) {
                return number;
            }
        }
        throw new SaplBinaryFormatException(MALFORMED);
    }

    /** A signed number as a zigzag varint. */
    private long signed() throws SaplBinaryFormatException {
        val zigzag = varint();
        return (zigzag >>> 1) ^ -(zigzag & 1);
    }

    /** The next {@code length} bytes of the region, which must end at a character boundary. */
    private String regionText(int length) throws SaplBinaryFormatException {
        val start = regionPosition;
        if (length > regionEnd - start) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        val stop = start + length;
        // The region is UTF-8 as a whole, so a text ends at a boundary unless the next
        // byte continues a character. It starts at one, as the text before it ended at one.
        if (stop < regionEnd && Utf8.isContinuation(payload[stop])) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        regionPosition = stop;
        return Utf8.decodeValid(payload, start, length);
    }

    /** The secrets of a subscription, which must be an object. */
    private ObjectValue secrets() throws SaplBinaryFormatException {
        if (position >= payload.length) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        val tag = payload[position] & 0xFF;
        if (tag == Tag.SHORT_OBJECT) {
            position++;
            return Value.EMPTY_OBJECT;
        }
        if (tag == Tag.OBJECT || (tag > Tag.SHORT_OBJECT && tag <= Tag.SHORT_OBJECT_LAST)) {
            return (ObjectValue) value();
        }
        throw new SaplBinaryFormatException(SECRETS_NOT_AN_OBJECT);
    }

    /** The obligations or the advice of a decision. Their list does not count as nesting. */
    private ArrayValue values() throws SaplBinaryFormatException {
        val count = length();
        if (count == 0) {
            return Value.EMPTY_ARRAY;
        }
        if (count > payload.length - position) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        val values = new Value[count];
        for (var i = 0; i < count; i++) {
            values[i] = value();
        }
        return new ArrayValue(values);
    }

    /**
     * One value. Arrays and objects open frames instead of recursing; each value
     * that is complete goes to the frame it belongs to, and closes every frame it
     * completes.
     */
    Value value() throws SaplBinaryFormatException {
        while (true) {
            var value = scalarOrOpen(nextByte());
            if (value == null) {
                continue;
            }
            while (true) {
                if (depth == 0) {
                    return value;
                }
                val top     = depth - 1;
                val builder = frameBuilder[top];
                if (builder != null) {
                    builder.put(frameKey[top], value);
                } else {
                    pushScratch(value);
                }
                if (--frameRemaining[top] > 0) {
                    if (builder != null) {
                        frameKey[top] = text();
                    }
                    break;
                }
                depth = top;
                value = close(top, builder);
            }
        }
    }

    /** The value of a tag, or null if the tag opened an array or object that is not empty. */
    private @Nullable Value scalarOrOpen(int tag) throws SaplBinaryFormatException {
        if (tag >= Tag.SMALL_INTEGER) {
            return Value.of((long) (tag - Tag.SMALL_INTEGER));
        }
        if (tag >= Tag.SHORT_OBJECT) {
            return open(tag - Tag.SHORT_OBJECT, true);
        }
        if (tag >= Tag.SHORT_ARRAY) {
            return open(tag - Tag.SHORT_ARRAY, false);
        }
        if (tag >= Tag.SHORT_TEXT) {
            return Value.of(regionText(tag - Tag.SHORT_TEXT));
        }
        return switch (tag) {
        case Tag.UNDEFINED   -> Value.UNDEFINED;
        case Tag.NULL        -> Value.NULL;
        case Tag.FALSE       -> Value.FALSE;
        case Tag.TRUE        -> Value.TRUE;
        case Tag.INTEGER     -> Value.of(signed());
        case Tag.DECIMAL     -> {
            val unscaled = signed();
            yield decimal(BigInteger.valueOf(unscaled), signed());
        }
        case Tag.BIG_DECIMAL -> bigDecimal();
        case Tag.ERROR       -> Value.error(text());
        case Tag.TEXT        -> Value.of(text());
        case Tag.ARRAY       -> open(length(), false);
        case Tag.OBJECT      -> open(length(), true);
        default              -> throw new SaplBinaryFormatException(MALFORMED);
        };
    }

    private Value bigDecimal() throws SaplBinaryFormatException {
        val scale  = signed();
        val length = length();
        if (length == 0 || length > MAX_BIG_DECIMAL_BYTES) {
            throw new SaplBinaryFormatException(INVALID_NUMBER);
        }
        if (length > payload.length - position) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        val unscaled = new BigInteger(payload, position, length);
        position += length;
        return decimal(unscaled, scale);
    }

    /** A number from its unscaled value and scale, refused if the scale is beyond the limits. */
    private static Value decimal(BigInteger unscaled, long scale) throws SaplBinaryFormatException {
        if (scale > NumberValueLimits.MAX_SCALE || scale < -NumberValueLimits.MAX_SCALE) {
            throw new SaplBinaryFormatException(INVALID_NUMBER);
        }
        return Value.of(new BigDecimal(unscaled, (int) scale));
    }

    /**
     * Opens an array or object of {@code count} elements or entries. An empty
     * one is complete at once and returned; for any other, a frame is opened,
     * the key of its first entry read, and null returned.
     */
    private @Nullable Value open(int count, boolean isObject) throws SaplBinaryFormatException {
        if (depth >= SaplBinaryCodec.MAX_DEPTH) {
            throw new SaplBinaryFormatException(TOO_DEEP);
        }
        if (count == 0) {
            return isObject ? Value.EMPTY_OBJECT : Value.EMPTY_ARRAY;
        }
        // Every element takes at least one byte, so a larger count cannot be true. Checking it
        // here keeps a lying count from reserving memory anywhere.
        if (count > payload.length - position) {
            throw new SaplBinaryFormatException(MALFORMED);
        }
        growFrames();
        frameRemaining[depth] = count;
        frameStart[depth]     = scratchSize;
        if (isObject) {
            frameBuilder[depth] = ObjectValue.builder();
            frameKey[depth]     = text();
        }
        depth++;
        return null;
    }

    /** The array or object of the frame at {@code index}, from its elements or its builder. */
    private Value close(int index, ObjectValue.Builder builder) {
        if (builder != null) {
            frameBuilder[index] = null;
            frameKey[index]     = null;
            return builder.build();
        }
        val start    = frameStart[index];
        val elements = Arrays.copyOfRange(scratch, start, scratchSize);
        Arrays.fill(scratch, start, scratchSize, null);
        scratchSize = start;
        return new ArrayValue(elements);
    }

    private void growFrames() {
        if (frameRemaining == null) {
            frameRemaining = new int[8];
            frameStart     = new int[8];
            frameBuilder   = new ObjectValue.Builder[8];
            frameKey       = new String[8];
        } else if (depth == frameRemaining.length) {
            val capacity = depth << 1;
            frameRemaining = Arrays.copyOf(frameRemaining, capacity);
            frameStart     = Arrays.copyOf(frameStart, capacity);
            frameBuilder   = Arrays.copyOf(frameBuilder, capacity);
            frameKey       = Arrays.copyOf(frameKey, capacity);
        }
    }

    private void pushScratch(Value value) {
        if (scratch == null) {
            scratch = new Value[16];
        } else if (scratchSize == scratch.length) {
            scratch = Arrays.copyOf(scratch, scratchSize << 1);
        }
        scratch[scratchSize++] = value;
    }
}
