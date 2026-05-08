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
 * Subject identification per OpenID Authorization API 1.0.
 *
 * @param type the subject type (e.g. "user")
 * @param id unique identifier scoped to the type
 * @param properties additional attributes available to policy evaluation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Entity requesting permission, identified by type and id with optional attributes.")
public record OpenIdSubject(
        @NotBlank @Schema(example = "user", requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @NotBlank @Schema(example = "alice@acmecorp.com", requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(description = "Free-form attributes (department, ip_address, device_id, ...).") Map<String, Object> properties) {}
