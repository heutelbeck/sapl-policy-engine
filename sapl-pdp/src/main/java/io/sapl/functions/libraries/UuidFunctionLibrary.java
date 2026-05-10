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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;

import java.util.UUID;

/**
 * Utility functions for UUID handling.
 */
@FunctionLibrary(name = UuidFunctionLibrary.NAME, description = UuidFunctionLibrary.DESCRIPTION, libraryDocumentation = UuidFunctionLibrary.DOCUMENTATION)
public class UuidFunctionLibrary {

    public static final String NAME        = "uuid";
    public static final String DESCRIPTION = "Utility functions for UUID handling.";

    public static final String DOCUMENTATION = """
            The UUID library provides functions for parsing Universally Unique Identifiers.
            Use it to inspect UUID-based resource identifiers from requests and extract their
            constituent parts for use in policy conditions.
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

    /**
     * Parses a text representation of a UUID and returns an object containing its
     * constituent parts. Returns an error
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
}
