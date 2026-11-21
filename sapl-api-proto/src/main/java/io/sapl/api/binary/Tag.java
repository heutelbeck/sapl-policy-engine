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

import lombok.experimental.UtilityClass;

/**
 * The tag bytes of the values of the SAPL binary format.
 */
@UtilityClass
class Tag {

    static final int UNDEFINED         = 0x00;
    static final int NULL              = 0x01;
    static final int FALSE             = 0x02;
    static final int TRUE              = 0x03;
    /** A whole number within 64 bits, as a zigzag varint. */
    static final int INTEGER           = 0x04;
    /** The unscaled value within 64 bits and the scale, both as zigzag varints. */
    static final int DECIMAL           = 0x05;
    /**
     * The scale as a zigzag varint, then the unscaled value as a length and
     * big-endian two's complement bytes.
     */
    static final int BIG_DECIMAL       = 0x06;
    static final int ERROR             = 0x07;
    static final int TEXT              = 0x08;
    static final int ARRAY             = 0x09;
    static final int OBJECT            = 0x0A;
    /** A text of up to 31 bytes, its length in the low five bits. */
    static final int SHORT_TEXT        = 0x20;
    /** An array of up to 31 elements, their number in the low five bits. */
    static final int SHORT_ARRAY       = 0x40;
    /** An object of up to 31 entries, their number in the low five bits. */
    static final int SHORT_OBJECT      = 0x60;
    static final int SHORT_OBJECT_LAST = 0x7F;
    /** A whole number from 0 to 127, in the low seven bits. */
    static final int SMALL_INTEGER     = 0x80;
    /** The longest text, array or object whose length is held in its tag. */
    static final int MAX_SHORT_LENGTH  = 31;
}
