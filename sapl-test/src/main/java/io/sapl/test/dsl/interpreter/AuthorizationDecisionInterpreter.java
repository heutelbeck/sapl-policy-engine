/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionType;
import io.sapl.test.grammar.sapltest.Value;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class AuthorizationDecisionInterpreter {

    private final ValueInterpreter valueInterpreter;
    private final ObjectMapper     objectMapper;

    AuthorizationDecision constructAuthorizationDecision(final AuthorizationDecisionType decisionType,
            final Value resource, final List<Value> obligations, final List<Value> advice) {
        if (decisionType == null) {
            throw new SaplTestException("AuthorizationDecisionType is null");
        }

        var authorizationDecision = getAuthorizationDecisionFromDSL(decisionType);

        if (resource != null) {
            final var mappedResource = valueInterpreter.getValFromValue(resource);
            authorizationDecision = authorizationDecision.withResource(mappedResource.get());
        }

        final var obligationArray = getMappedValArrayFromValues(obligations);

        if (obligationArray != null) {
            authorizationDecision = authorizationDecision.withObligations(obligationArray);
        }

        final var adviceArray = getMappedValArrayFromValues(advice);

        if (adviceArray != null) {
            authorizationDecision = authorizationDecision.withAdvice(adviceArray);
        }

        return authorizationDecision;
    }

    private ArrayNode getMappedValArrayFromValues(final List<Value> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        final var valArray = objectMapper.createArrayNode();

        values.stream().map(valueInterpreter::getValFromValue).map(io.sapl.api.interpreter.Val::get)
                .forEach(valArray::add);

        return valArray;
    }

    private AuthorizationDecision getAuthorizationDecisionFromDSL(final AuthorizationDecisionType decision) {
        return switch (decision) {
        case PERMIT -> AuthorizationDecision.PERMIT;
        case DENY -> AuthorizationDecision.DENY;
        case INDETERMINATE -> AuthorizationDecision.INDETERMINATE;
        case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
        };
    }
}
