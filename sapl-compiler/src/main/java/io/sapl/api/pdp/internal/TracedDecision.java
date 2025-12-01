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
package io.sapl.api.pdp.internal;

import io.sapl.api.pdp.AuthorizationDecision;

/**
 * A policy decision with trace metadata for debugging and auditing.
 */
public interface TracedDecision {

    /**
     * @return the authorization decision
     */
    AuthorizationDecision getAuthorizationDecision();

    /**
     * @return the decision metadata
     */
    DecisionMetadata getMetadata();

    /**
     * Creates a modified traced decision with an explanation for audit purposes.
     *
     * @param decision the new authorization decision
     * @param explanation why the decision was modified
     * @return a new TracedDecision with the modified decision
     */
    TracedDecision modified(AuthorizationDecision decision, String explanation);
}
