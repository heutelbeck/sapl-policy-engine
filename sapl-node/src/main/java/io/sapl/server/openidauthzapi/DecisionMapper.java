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

import io.sapl.api.model.UndefinedValue;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a SAPL {@link AuthorizationDecision} into an
 * {@link OpenIdEvaluationResponse} per the OpenID Authorization API 1.0
 * binary-decision contract.
 * <p>
 * SAPL has five decision verbs; OpenID has a boolean. PERMIT becomes true.
 * DENY, NOT_APPLICABLE, INDETERMINATE, and SUSPEND all become false. The
 * response context carries the lossy detail back to SAPL-aware clients.
 */
@UtilityClass
public class DecisionMapper {

    static final String SAPL_KEY          = "sapl";
    static final String OBLIGATIONS_KEY   = "obligations";
    static final String ADVICE_KEY        = "advice";
    static final String RESOURCE_KEY      = "resource";
    static final String SAPL_DECISION_KEY = "decision";
    static final String REASON_ADMIN_KEY  = "reason_admin";
    static final String REASON_USER_KEY   = "reason_user";
    static final String LANG_EN           = "en";
    static final String LANG_EN_403       = "en-403";

    static final String SUSPEND_MARKER          = "SUSPEND";
    static final String REASON_INDETERMINATE_EN = "Policy evaluation could not reach a conclusion.";
    static final String REASON_SUSPEND_EN_403   = "Authorization deferred. Retry after additional authentication.";

    public static OpenIdEvaluationResponse map(AuthorizationDecision sapl, ObjectMapper mapper) {
        final boolean             permitted = sapl.decision() == Decision.PERMIT;
        final Map<String, Object> ctx       = buildContext(sapl, mapper);
        return new OpenIdEvaluationResponse(permitted, ctx.isEmpty() ? null : ctx);
    }

    private static Map<String, Object> buildContext(AuthorizationDecision sapl, ObjectMapper mapper) {
        final var ctx     = new LinkedHashMap<String, Object>();
        final var saplExt = buildSaplExtensions(sapl, mapper);

        switch (sapl.decision()) {
        case PERMIT, DENY, NOT_APPLICABLE -> {
            // No reason fields. SAPL extensions added below if present.
        }
        case INDETERMINATE                -> ctx.put(REASON_ADMIN_KEY, Map.of(LANG_EN, REASON_INDETERMINATE_EN));
        case SUSPEND                      -> {
            ctx.put(REASON_USER_KEY, Map.of(LANG_EN_403, REASON_SUSPEND_EN_403));
            saplExt.put(SAPL_DECISION_KEY, SUSPEND_MARKER);
        }
        }

        if (!saplExt.isEmpty()) {
            ctx.put(SAPL_KEY, saplExt);
        }
        return ctx;
    }

    private static Map<String, Object> buildSaplExtensions(AuthorizationDecision sapl, ObjectMapper mapper) {
        final var ext = new LinkedHashMap<String, Object>();
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
