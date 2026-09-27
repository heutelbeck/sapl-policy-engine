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
package io.sapl.attributeapi.attributes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

/*
 * The attribute publish request that is used in the HTTP body.
 */
public record AttributePublishRequest(
        @Schema(description = DESC_TTL, example = "600") Long ttl,
        @Schema(description = DESC_VALUE, type = "string", example = "IT") JsonNode value) {
    private static final String DESC_VALUE = "The value to publish or update the attribute as a JSON literal.";
    private static final String DESC_TTL = "The time to live (TTL) in seconds for the attribute. If not set, the attribute's lifetime is unlimited.";
}
