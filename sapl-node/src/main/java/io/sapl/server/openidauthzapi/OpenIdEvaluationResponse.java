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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Access evaluation response per OpenID Authorization API 1.0 section 5.
 *
 * @param decision true to permit, false to deny
 * @param context optional reason fields and SAPL extensions; omitted when null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Access evaluation response. Carries the boolean decision and an optional context with reasons or SAPL extensions.")
record OpenIdEvaluationResponse(
        @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean decision,
        @Schema(description = "Optional reason fields (reason_admin, reason_user) and SAPL extensions (sapl.obligations, sapl.advice, sapl.resource, sapl.decision).") Map<String, Object> context) {}
