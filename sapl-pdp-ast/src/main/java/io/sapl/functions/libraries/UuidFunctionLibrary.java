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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Random;
import java.util.UUID;

/**
 * Utility functions for UUID handling.
 */
@UtilityClass
@FunctionLibrary(name = UuidFunctionLibrary.NAME, description = UuidFunctionLibrary.DESCRIPTION, libraryDocumentation = UuidFunctionLibrary.DOCUMENTATION)
public class UuidFunctionLibrary {

    public static final String NAME        = "uuid";
    public static final String DESCRIPTION = "Utility functions for UUID handling.";

    public static final String DOCUMENTATION = """
            The UUID library provides functions for generating and parsing Universally Unique Identifiers.
            Use it when you need unique identifiers for correlation, tracking, or deduplication in policies.

            Common use cases include generating request IDs for audit trails, creating unique session tokens,
            or parsing UUID-based resource identifiers from requests. The library supports both cryptographically
            secure random UUIDs for production and deterministic seeded UUIDs for testing.

            **Example:**
            ```sapl
            policy "audit_with_request_id"
            permit
            where
              var requestId = uuid.random();
              // Use requestId for audit correlation
            obligation
              {
                "type": "log",
                "requestId": requestId,
                "action": action.method
              }
            ```
            """;

    private static final String ERROR_INVALID_UUID = "Invalid UUID: %s.";

    private static final String FIELD_CLOCK_SEQUENCE    = "clockSequence";
    private static final String FIELD_LEAST_SIGNIFICANT = "leastSignificantBits";
    private static final String FIELD_MOST_SIGNIFICANT  = "mostSignificantBits";
    private static final String FIELD_NODE              = "node";
    private static final String FIELD_TIMESTAMP         = "timestamp";
    private static final String FIELD_VARIANT           = "variant";
    private static final String FIELD_VERSION           = "version";

    private static final String SCHEMA_RETURNS_UUID_OBJECT = """
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

    private static final int UUID_BYTE_SIZE          = 16;
    private static final int UUID_MOST_SIG_BYTE_SIZE = 8;

    /**
     * Parses a text representation of a UUID and returns an object containing its
     * constituent parts. Returns an errors
     * if the text is not a valid UUID string.
     *
     * @param uuidText
     * the text representation of the UUID to parse
     *
     * @return an ObjectValue containing the UUID parts, or an ErrorValue if invalid
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
            """, schema = SCHEMA_RETURNS_UUID_OBJECT)
    public static Value parse(TextValue uuidText) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText.value());
        } catch (IllegalArgumentException exception) {
            return Value.error(ERROR_INVALID_UUID, exception.getMessage());
        }

        val resultBuilder = ObjectValue.builder();
        resultBuilder.put(FIELD_LEAST_SIGNIFICANT, Value.of(uuid.getLeastSignificantBits()));
        resultBuilder.put(FIELD_MOST_SIGNIFICANT, Value.of(uuid.getMostSignificantBits()));
        resultBuilder.put(FIELD_VERSION, Value.of(uuid.version()));
        resultBuilder.put(FIELD_VARIANT, Value.of(uuid.variant()));

        try {
            resultBuilder.put(FIELD_TIMESTAMP, Value.of(uuid.timestamp()));
            resultBuilder.put(FIELD_CLOCK_SEQUENCE, Value.of(uuid.clockSequence()));
            resultBuilder.put(FIELD_NODE, Value.of(uuid.node()));
        } catch (UnsupportedOperationException ignored) {
            // Version is not a version 1 UUID - optional fields not added
        }

        return resultBuilder.build();
    }

    /**
     * Generates a cryptographically strong random version 4 UUID. This method uses
     * a secure random number generator to
     * produce unpredictable UUIDs suitable for production use and
     * security-sensitive contexts.
     *
     * @return a TextValue containing a randomly generated UUID string
     */
    @Function(docs = """
            ```uuid.random()```: Generates a cryptographically strong random version 4 UUID using a secure
            random number generator. Returns the UUID as a text string. This function produces unpredictable
            UUIDs suitable for production use and security-sensitive contexts.

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var randomUuid = uuid.random();
              // Generates a new random UUID like "a3bb189e-8bf9-3888-9912-ace4e6543002"

              var anotherUuid = uuid.random();
              // Generates a different random UUID

              randomUuid != anotherUuid;  // true, each call produces a unique UUID
            ```
            """)
    public static Value random() {
        return Value.of(UUID.randomUUID().toString());
    }

    /**
     * Creates a version 4 (pseudo-random) UUID using a seeded random number
     * generator. This method produces
     * deterministic UUIDs based on the provided seed, making it suitable for
     * testing or scenarios requiring
     * reproducible UUID generation.
     *
     * @param seed
     * the seed value for the random number generator
     *
     * @return a TextValue containing a UUID generated using the seeded random
     * number generator
     */
    @Function(docs = """
            ```uuid.seededRandom(INT seed)```: Generates a deterministic version 4 (pseudo-random) UUID using
            a seeded random number generator. Returns the UUID as a text string. This function produces
            reproducible UUIDs based on the provided seed, making it suitable for testing or scenarios
            requiring deterministic UUID generation.

            **Attention:** This function uses a seeded random number generator and produces predictable results.
            It is NOT cryptographically secure and should not be used for security-sensitive contexts.
            For production use requiring true randomness, use uuid.random() instead.

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
    public static Value seededRandom(NumberValue seed) {
        val random      = new Random(seed.value().longValue());
        val randomBytes = new byte[UUID_BYTE_SIZE];
        random.nextBytes(randomBytes);
        randomBytes[6] &= 0x0f; // clear version
        randomBytes[6] |= 0x40; // set to version 4
        randomBytes[8] &= 0x3f; // clear variant
        randomBytes[8] |= (byte) 0x80; // set to IETF variant

        long mostSigBits  = 0;
        long leastSigBits = 0;

        // Convert first 8 bytes to most significant bits
        for (int i = 0; i < UUID_MOST_SIG_BYTE_SIZE; i++) {
            mostSigBits = (mostSigBits << 8) | (randomBytes[i] & 0xff);
        }
        // Convert last 8 bytes to least significant bits
        for (int i = UUID_MOST_SIG_BYTE_SIZE; i < UUID_BYTE_SIZE; i++) {
            leastSigBits = (leastSigBits << 8) | (randomBytes[i] & 0xff);
        }

        return Value.of(new UUID(mostSigBits, leastSigBits).toString());
    }
}
