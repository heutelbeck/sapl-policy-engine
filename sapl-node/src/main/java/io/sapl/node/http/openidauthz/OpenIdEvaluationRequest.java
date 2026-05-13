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
package io.sapl.node.http.openidauthz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Access evaluation request per OpenID Authorization API 1.0 section 4.
 *
 * @param subject the entity requesting permission
 * @param action the activity for which permission is requested
 * @param resource the resource being accessed
 * @param context optional environmental attributes (time, IP, request id, ...)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Access evaluation request with subject, action, resource, and optional context.")
record OpenIdEvaluationRequest(
        @Valid @NotNull OpenIdSubject subject,
        @Valid @NotNull OpenIdAction action,
        @Valid @NotNull OpenIdResource resource,
        @Schema(description = "Free-form environmental attributes.") Map<String, Object> context) {}
