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
package io.sapl.springdatacommon.services;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.springdatacommon.utils.Utilities;
import lombok.Getter;

@Getter
public enum ConstraintHandlerType {

    R2DBC_QUERY_MANIPULATION("r2dbcQueryManipulation", getTemplateQueryManipulation()),
    MONGO_QUERY_MANIPULATION("mongoQueryManipulation", getTemplateQueryManipulation());

    private final String   type;
    private final JsonNode template;

    ConstraintHandlerType(String type, JsonNode template) {
        this.type     = type;
        this.template = template;
    }

    public static JsonNode getQueryManipulationSelectionStructure() {
        return Utilities.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "selection": {
                      "type": "object",
                      "properties": {
                        "type": {
                          "type": "string"
                        },
                        "columns": {
                          "type": "array",
                          "items": {
                            "type": "string"
                          }
                        }
                      },
                      "required": ["type", "columns"],
                      "additionalProperties": false
                    }
                  },
                  "required": ["selection"]
                }
                                """);
    }

    private static JsonNode getTemplateQueryManipulation() {
        return Utilities.readTree("""
                  {
                  "type": "object",
                  "properties": {
                    "type": {
                      "type": "string"
                    },
                    "conditions": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    }
                  },
                  "required": ["type", "conditions"]
                }
                """);
    }

}
