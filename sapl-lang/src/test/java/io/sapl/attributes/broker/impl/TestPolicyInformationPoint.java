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
package io.sapl.attributes.broker.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.*;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

import java.util.Map;

@UtilityClass
@PolicyInformationPoint(name = "test")
public class TestPolicyInformationPoint {
    private static final String SCHEMA = """
            {
              "$id": "https://example.com/person.schema.json",
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "title": "Person",
              "type": "object",
              "properties": {
                "firstName": {
                  "type": "string",
                  "description": "The person's first name."
                },
                "lastName": {
                  "type": "string",
                  "description": "The person's last name."
                },
                "age": {
                  "description": "Age in years which must be equal to or greater than zero.",
                  "type": "integer",
                  "minimum": 0
                }
              }
            }
            """;

    @Attribute
    public static Mono<Val> a1(@Text Val entity, Map<String, Val> variables, @Text Val p1,
            @Text @Schema(SCHEMA) @Number @Long @Int @Bool @Array @JsonObject Val p2) {
        return Mono.just(Val.of("output a1"));
    }
}
