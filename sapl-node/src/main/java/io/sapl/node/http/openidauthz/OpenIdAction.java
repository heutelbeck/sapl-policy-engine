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
 * Action identification per OpenID Authorization API 1.0.
 *
 * @param name the action name (e.g. "can_read", "can_delete")
 * @param properties additional action attributes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Activity for which permission is requested.")
record OpenIdAction(
        @NotBlank @Schema(example = "can_read", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "Free-form action attributes.") Map<String, Object> properties) {}
