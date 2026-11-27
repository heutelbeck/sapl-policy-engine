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

import io.sapl.api.model.UndefinedValue;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a SAPL {@link AuthorizationDecision} into an
 * {@link OpenIdEvaluationResponse} per the OpenID Authorization API 1.0
 * binary-decision contract.
 * <p>
 * SAPL has five decision verbs and three constraint slots. The OpenID
 * boolean is necessarily lossy. The mapping is:
 * <ul>
 * <li><b>true</b>: only for a PERMIT carrying no obligations and no
 * transformed resource. A vanilla OpenID PEP that reads only the
 * boolean and ignores the optional context can safely grant access.
 * Advice may still be present (it is recommendatory, not enforceable).</li>
 * <li><b>false</b>: every other case, including DENY, INDETERMINATE,
 * NOT_APPLICABLE, SUSPEND, and any PERMIT that carries obligations or
 * a transformed resource. Obligations MUST be enforced and a
 * transformed resource MUST replace the original on the wire. A PEP
 * that cannot read and act on the context must therefore treat such a
 * decision as a denial.</li>
 * </ul>
 * The verb, obligations, advice and transformed resource are always
 * surfaced under {@code context.sapl.*} so SAPL-aware clients can
 * recover the full decision. A human-readable reason is always
 * present alongside whenever the boolean is {@code false}:
 * {@code reason_admin} for INDETERMINATE and PERMIT-needs-enforcement,
 * {@code reason_user} for SUSPEND, and a denial reason for DENY /
 * NOT_APPLICABLE.
 */
@UtilityClass
class DecisionMapper {

    static final String ADVICE_KEY                         = "advice";
    static final String LANG_EN                            = "en";
    static final String LANG_EN_403                        = "en-403";
    static final String OBLIGATIONS_KEY                    = "obligations";
    static final String REASON_ADMIN_KEY                   = "reason_admin";
    static final String REASON_DENY_EN                     = "Access denied by policy.";
    static final String REASON_INDETERMINATE_EN            = "Policy evaluation could not reach a conclusion.";
    static final String REASON_NOT_APPLICABLE_EN           = "No policy matched the request.";
    static final String REASON_PERMIT_NEEDS_ENFORCEMENT_EN = "Permit requires SAPL-aware enforcement of obligations or a transformed resource; mapped to deny for vanilla OpenID PEPs.";
    static final String REASON_SUSPEND_EN_403              = "Authorization deferred. Retry after additional authentication.";
    static final String REASON_USER_KEY                    = "reason_user";
    static final String RESOURCE_KEY                       = "resource";
    static final String SAPL_DECISION_KEY                  = "decision";
    static final String SAPL_KEY                           = "sapl";

    static OpenIdEvaluationResponse map(AuthorizationDecision sapl, ObjectMapper mapper) {
        final boolean             permitted = isUnambiguouslyPermitted(sapl);
        final Map<String, Object> ctx       = buildContext(sapl, permitted, mapper);
        return new OpenIdEvaluationResponse(permitted, ctx);
    }

    private static boolean isUnambiguouslyPermitted(AuthorizationDecision sapl) {
        return sapl.decision() == Decision.PERMIT && sapl.obligations().isEmpty()
                && sapl.resource() instanceof UndefinedValue;
    }

    private record Reason(String key, Map<String, String> value) {}

    private static Map<String, Object> buildContext(AuthorizationDecision sapl, boolean permitted,
            ObjectMapper mapper) {
        val ctx     = new LinkedHashMap<String, Object>();
        val saplExt = buildSaplExtensions(sapl, mapper);

        // Always surface the SAPL verb under sapl.decision. The OpenID boolean
        // is lossy (PERMIT with obligations or a transformed resource also maps
        // to false). SAPL-aware clients reconstruct the original decision here.
        saplExt.put(SAPL_DECISION_KEY, sapl.decision().name());

        // A reason is present whenever the boolean is false, so a vanilla OpenID
        // PEP that ignores the SAPL extensions still has something to log or
        // surface. Switch expression makes adding a new Decision verb a compile
        // error.
        Reason reason = switch (sapl.decision()) {
        case PERMIT         ->
            permitted ? null : new Reason(REASON_ADMIN_KEY, Map.of(LANG_EN, REASON_PERMIT_NEEDS_ENFORCEMENT_EN));
        case DENY           -> new Reason(REASON_USER_KEY, Map.of(LANG_EN_403, REASON_DENY_EN));
        case NOT_APPLICABLE -> new Reason(REASON_USER_KEY, Map.of(LANG_EN_403, REASON_NOT_APPLICABLE_EN));
        case INDETERMINATE  -> new Reason(REASON_ADMIN_KEY, Map.of(LANG_EN, REASON_INDETERMINATE_EN));
        case SUSPEND        -> new Reason(REASON_USER_KEY, Map.of(LANG_EN_403, REASON_SUSPEND_EN_403));
        };
        if (reason != null) {
            ctx.put(reason.key(), reason.value());
        }

        ctx.put(SAPL_KEY, saplExt);
        return ctx;
    }

    private static Map<String, Object> buildSaplExtensions(AuthorizationDecision sapl, ObjectMapper mapper) {
        val ext = new LinkedHashMap<String, Object>();
        if (!sapl.obligations().isEmpty()) {
            ext.put(OBLIGATIONS_KEY, mapper.valueToTree(sapl.obligations()));
        }
        if (!sapl.advice().isEmpty()) {
            ext.put(ADVICE_KEY, mapper.valueToTree(sapl.advice()));
        }
        if (!(sapl.resource() instanceof UndefinedValue)) {
            ext.put(RESOURCE_KEY, mapper.valueToTree(sapl.resource()));
        }
        return ext;
    }
}
