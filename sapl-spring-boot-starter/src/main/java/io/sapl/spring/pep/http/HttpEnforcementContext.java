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
package io.sapl.spring.pep.http;

import io.sapl.spring.pep.constraints.EnforcementPlan;

/**
 * Shared constants for HTTP-layer enforcement. The authorization managers
 * publish the active {@link EnforcementPlan} on the request or exchange
 * under {@link #PLAN_ATTRIBUTE}. Downstream collaborators (the SAPL HTTP
 * filter and the SAPL access-denied handler) read it from there.
 */
public final class HttpEnforcementContext {

    /**
     * Attribute key under which the SAPL authorization managers publish the
     * active {@link EnforcementPlan}. Used by the HTTP filter and the
     * access-denied handler to fire HTTP-layer signals against the same
     * plan the manager built.
     */
    public static final String PLAN_ATTRIBUTE = EnforcementPlan.class.getName();

    private HttpEnforcementContext() {
    }
}
