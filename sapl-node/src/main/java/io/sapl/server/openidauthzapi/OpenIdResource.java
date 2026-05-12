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
package io.sapl.server.openidauthzapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Resource identification per OpenID Authorization API 1.0.
 *
 * @param type the resource type (e.g. "book", "account")
 * @param id unique identifier scoped to the type
 * @param properties additional attributes available to policy evaluation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Resource being accessed, identified by type and id with optional attributes.")
record OpenIdResource(
        @NotBlank @Schema(example = "book", requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @NotBlank @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(description = "Free-form resource attributes.") Map<String, Object> properties) {}
