/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
class AuthorizationDecisionInterpreter {

    private final ValueInterpreter valueInterpreter;

    AuthorizationDecision constructAuthorizationDecision(AuthorizationDecisionType decisionType,
            io.sapl.test.grammar.sapltest.Value resource, List<io.sapl.test.grammar.sapltest.Value> obligations,
            List<io.sapl.test.grammar.sapltest.Value> advice) {
        if (null == decisionType) {
            throw new SaplTestException("AuthorizationDecisionType is null.");
        }

        var decision = getDecisionFromDSL(decisionType);

        var resourceValue   = resource != null ? valueInterpreter.getValueFromDslValue(resource) : Value.UNDEFINED;
        var obligationsList = getMappedValuesFromValues(obligations);
        var adviceList      = getMappedValuesFromValues(advice);

        return new AuthorizationDecision(decision, obligationsList, adviceList, resourceValue);
    }

    private List<Value> getMappedValuesFromValues(List<io.sapl.test.grammar.sapltest.Value> values) {
        if (null == values || values.isEmpty()) {
            return List.of();
        }

        return values.stream().map(valueInterpreter::getValueFromDslValue).toList();
    }

    private Decision getDecisionFromDSL(AuthorizationDecisionType decision) {
        return switch (decision) {
        case PERMIT         -> Decision.PERMIT;
        case DENY           -> Decision.DENY;
        case INDETERMINATE  -> Decision.INDETERMINATE;
        case NOT_APPLICABLE -> Decision.NOT_APPLICABLE;
        };
    }
}
