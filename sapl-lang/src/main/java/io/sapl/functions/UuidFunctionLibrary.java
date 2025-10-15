/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Random;
import java.util.UUID;

@UtilityClass
@FunctionLibrary(name = UuidFunctionLibrary.NAME, description = UuidFunctionLibrary.DESCRIPTION)
public class UuidFunctionLibrary {
    public static final String NAME        = "uuid";
    public static final String DESCRIPTION = "Utility functions for UUID handling.";

    private static final String RETURNS_UUID_OBJECT = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "https://example.com/uuid.schema.json",
              "title": "UUID",
              "description": "Schema describing a UUID with its constituent parts",
              "type": "object",
              "properties": {
                "leastSignificantBits": {
                  "type": "number",
                  "description": "The least significant 64 bits of the UUID"
                },
                "mostSignificantBits": {
                  "type": "number",
                  "description": "The most significant 64 bits of the UUID"
                },
                "version": {
                  "type": "number",
                  "description": "The version number associated with this UUID"
                },
                "variant": {
                  "type": "number",
                  "description": "The variant number associated with this UUID"
                },
                "timestamp": {
                  "type": "number",
                  "description": "The timestamp value (only available for version 1 UUIDs)"
                },
                "clockSequence": {
                  "type": "number",
                  "description": "The clock sequence value (only available for version 1 UUIDs)"
                },
                "node": {
                  "type": "number",
                  "description": "The node value (only available for version 1 UUIDs)"
                }
              },
              "required": [
                "leastSignificantBits",
                "mostSignificantBits",
                "version",
                "variant"
              ],
              "additionalProperties": false
            }
            """;

    /**
     * Parses a text representation of a UUID and returns an object containing its
     * constituent parts.
     * Returns an error if the text is not a valid UUID string.
     *
     * @param uuidVal the text representation of the UUID to parse
     * @return a Val containing the UUID object with its parts, or an error
     */
    @Function(docs = """
            ```uuid.parse(TEXT uuid)```: Parses a text representation of a UUID and returns an object containing
            the UUID's constituent parts including the least and most significant bits, version, and variant.
            For version 1 UUIDs, the object also includes timestamp, clock sequence, and node values.
            Returns an error if the text is not a valid UUID string.

            **Attention:** The timestamp, clockSequence, and node fields are only present for version 1 UUIDs.
            Other UUID versions will only contain the leastSignificantBits, mostSignificantBits, version, and
            variant fields.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var parsedUuid = uuid.parse("550e8400-e29b-41d4-a716-446655440000");
              // Returns object with leastSignificantBits, mostSignificantBits, version, variant

              var version1Uuid = uuid.parse("c232ab00-9414-11ec-b3c8-9f6bdeced846");
              // Returns object with additional timestamp, clockSequence, and node fields

              var invalidUuid = uuid.parse("not-a-uuid");
              // Returns an error
             ```
            """, schema = RETURNS_UUID_OBJECT)
    public static Val parse(@Text Val uuidVal) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidVal.getText());
        } catch (IllegalArgumentException e) {
            return Val.error(e.getMessage());
        }
        val uuidObj = Val.JSON.objectNode();
        uuidObj.set("leastSignificantBits", Val.JSON.numberNode(uuid.getLeastSignificantBits()));
        uuidObj.set("mostSignificantBits", Val.JSON.numberNode(uuid.getMostSignificantBits()));
        uuidObj.set("version", Val.JSON.numberNode(uuid.version()));
        uuidObj.set("variant", Val.JSON.numberNode(uuid.variant()));
        try {
            uuidObj.set("timestamp", Val.JSON.numberNode(uuid.timestamp()));
            uuidObj.set("clockSequence", Val.JSON.numberNode(uuid.clockSequence()));
            uuidObj.set("node", Val.JSON.numberNode(uuid.node()));
        } catch (UnsupportedOperationException e) {
            /* no-op this version is not a version 1 UUID */
        }
        return Val.of(uuidObj);
    }

    /**
     * Creates a version 4 (pseudo-random) UUID using a seeded random number
     * generator.
     * This method produces deterministic UUIDs based on the provided seed, making
     * it
     * suitable for testing or scenarios requiring reproducible UUID generation.
     *
     * @param seed the seed value for the random number generator
     * @return a UUID generated using the seeded random number generator
     */
    @Function(docs = """
            ```uuid.seededRandom(INT seed)```: Generates a deterministic version 4 (pseudo-random) UUID using
            a seeded random number generator. Returns the UUID as a text string. This function produces
            reproducible UUIDs based on the provided seed, making it suitable for testing or scenarios
            requiring deterministic UUID generation.

            **Attention:** This function uses a seeded random number generator and produces predictable results.
            It is NOT cryptographically secure and should not be used for security-sensitive contexts.
            For production use requiring true randomness, use a different UUID generation method.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var testUuid = uuid.seededRandom(12345);
              // Always generates the same UUID for seed 12345

              var anotherUuid = uuid.seededRandom(67890);
              // Generates a different but deterministic UUID for seed 67890

              testUuid == uuid.seededRandom(12345);  // true, same seed produces same UUID
            ```
            """)
    public static Val seededRandom(@Int Val seed) {
        val    random      = new Random(seed.getLong());
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        randomBytes[6] &= 0x0f;        /* clear version */
        randomBytes[6] |= 0x40;        /* set to version 4 */
        randomBytes[8] &= 0x3f;        /* clear variant */
        randomBytes[8] |= (byte) 0x80; /* set to IETF variant */

        long mostSigBits  = 0;
        long leastSigBits = 0;

        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (randomBytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (randomBytes[i] & 0xff);
        }

        return Val.of(new UUID(mostSigBits, leastSigBits).toString());
    }

}
