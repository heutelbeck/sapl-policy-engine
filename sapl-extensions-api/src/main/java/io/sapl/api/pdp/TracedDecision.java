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
package io.sapl.api.pdp;

import java.io.Serializable;

import io.sapl.api.interpreter.Traced;

/**
 * A decision which is potentially changed by interceptors. Interceptors should
 * add an explanation into the trace.
 */
public interface TracedDecision extends Traced, Serializable {
    /**
     * @return the decision.
     */
    AuthorizationDecision getAuthorizationDecision();

    /**
     * Add an explanation to a modified decision.
     *
     * @param authzDecision the modified decision
     * @param explanation   the explanation
     * @return the modified decision with explanation
     */
    TracedDecision modified(AuthorizationDecision authzDecision, String explanation);
}
