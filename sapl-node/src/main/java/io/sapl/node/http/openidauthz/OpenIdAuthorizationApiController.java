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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * REST controller exposing the OpenID Authorization API 1.0 access evaluation
 * endpoint, backed by the SAPL Policy Decision Point.
 * <p>
 * Reference: https://openid.net/specs/authorization-api-1_0-01.html
 * <p>
 * The OpenID API is a strict subset of the SAPL PDP API at /api/pdp: a
 * single one-shot evaluation, boolean decision, no streaming, no batching.
 * The full SAPL decision (verb, obligations, advice, transformed resource) is
 * surfaced through the response context for SAPL-aware clients.
 */
@Slf4j
@Profile("!cli")
@RestController
@RequiredArgsConstructor
@RequestMapping("/access/v1")
@Tag(name = "OpenID Authorization API", description = "OpenID Authorization API 1.0 binding for the SAPL PDP. Returns a single boolean decision per request.")
@ConditionalOnProperty(name = "io.sapl.server.openid-authz-api.enabled", matchIfMissing = true)
public class OpenIdAuthorizationApiController {

    private static final String X_REQUEST_ID = "X-Request-ID";

    private final BlockingPolicyDecisionPoint pdp;
    private final BlockingTenantResolver      tenantResolver;
    private final ObjectMapper                objectMapper;

    /**
     * Evaluates an access request and returns a binary decision per OpenID
     * Authorization API 1.0.
     *
     * @param request the access evaluation request (subject, action, resource,
     * optional context)
     * @param requestId optional X-Request-ID header to echo in the response
     * @return the access evaluation response wrapped in a {@link ResponseEntity}
     */
    @Operation(summary = "Evaluate an access request", description = """
            Returns a boolean access decision per OpenID Authorization API 1.0.

            The response body always uses HTTP 200, including for INDETERMINATE evaluations \
            (per spec). Non-2xx is reserved for request-level errors (malformed JSON, missing \
            fields, authentication failure).

            The boolean is `true` only for a PERMIT that carries no obligations and no \
            transformed resource. DENY, INDETERMINATE, NOT_APPLICABLE, SUSPEND, and any \
            PERMIT that carries obligations or a transformed resource map to `false`. \
            Access is therefore granted only when the SAPL decision would permit it \
            unconditionally.

            Whenever the boolean is `false` the response also carries a `reason_admin` \
            field (technical, for INDETERMINATE and PERMIT-needs-enforcement) or a \
            `reason_user` field (subject-facing, for DENY, NOT_APPLICABLE, and SUSPEND). \
            SAPL-aware clients read `context.sapl.{decision,obligations,advice,resource}` \
            for the full decision.

            The `X-Request-ID` request header is echoed in the response when present.""")
    @PostMapping(value = "/evaluation", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OpenIdEvaluationResponse> evaluate(@Valid @RequestBody OpenIdEvaluationRequest request,
            @RequestHeader(value = X_REQUEST_ID, required = false) String requestId) {
        val                   subscription = toSubscription(request);
        AuthorizationDecision decision;
        try {
            val pdpId = tenantResolver.resolve();
            decision = pdp.decideOnce(subscription, pdpId);
        } catch (Exception error) {
            log.error("Error during OpenID access evaluation: {}", error.getMessage(), error);
            decision = AuthorizationDecision.INDETERMINATE;
        }
        val body = DecisionMapper.map(decision, objectMapper);
        return withRequestId(ResponseEntity.ok(), requestId).body(body);
    }

    private AuthorizationSubscription toSubscription(OpenIdEvaluationRequest request) {
        final Object noSecrets = null;
        return AuthorizationSubscription.of(request.subject(), request.action(), request.resource(), request.context(),
                noSecrets, objectMapper);
    }

    private static ResponseEntity.BodyBuilder withRequestId(ResponseEntity.BodyBuilder builder, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            builder.header(X_REQUEST_ID, requestId);
        }
        return builder;
    }
}
