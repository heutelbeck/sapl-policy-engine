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
 * @param decision {@code true} only when the SAPL decision is PERMIT with no
 * obligations and no transformed resource; {@code false} for DENY,
 * NOT_APPLICABLE, INDETERMINATE, SUSPEND, and any PERMIT that carries
 * obligations or a transformed resource (which a vanilla OpenID PEP cannot
 * enforce). See {@link DecisionMapper} for the full mapping.
 * @param context reason fields and SAPL extensions. When {@code decision} is
 * {@code false} this carries either a {@code reason_admin} or a
 * {@code reason_user} entry. The verb plus any obligations / advice /
 * transformed resource are always surfaced under
 * {@code context.sapl.*} so SAPL-aware clients can recover the full
 * decision. Omitted entirely only when the response carries no context
 * (currently never the case).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Access evaluation response. Carries the boolean decision and an optional context with reasons or SAPL extensions.")
record OpenIdEvaluationResponse(
        @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean decision,
        @Schema(description = "Optional reason fields (reason_admin, reason_user) and SAPL extensions (sapl.obligations, sapl.advice, sapl.resource, sapl.decision).") Map<String, Object> context) {}
