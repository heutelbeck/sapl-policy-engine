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
package io.sapl.api.model.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.sapl.api.pdp.CombiningAlgorithm;

import java.io.IOException;

/**
 * Deserializer for {@link CombiningAlgorithm} that supports case-insensitive
 * parsing and kebab-case format (e.g., "deny-overrides" maps to DENY_OVERRIDES).
 */
public class CombiningAlgorithmDeserializer extends JsonDeserializer<CombiningAlgorithm> {

    @Override
    public CombiningAlgorithm deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        var text = parser.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return CombiningAlgorithm.valueOf(text.replace('-', '_').toUpperCase());
    }

}
