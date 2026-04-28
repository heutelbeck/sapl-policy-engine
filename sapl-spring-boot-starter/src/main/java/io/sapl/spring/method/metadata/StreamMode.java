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
package io.sapl.spring.method.metadata;

/**
 * Lifecycle policy for a streaming PEP. Selects what happens to the
 * subscription on a deny or unenforceable PERMIT.
 * <p>
 * Default for {@link StreamEnforce} is {@link #TILL_DENIED} (strictest;
 * fail-closed).
 * <p>
 * The mode is part of the PEP's runtime state. A new decision arriving
 * mid-stream can change the active mode (when the PDP supplies one),
 * causing a state-machine transition without resubscribing.
 */
public enum StreamMode {

    /**
     * The subscription terminates with an
     * {@link org.springframework.security.access.AccessDeniedException} on
     * the first deny or unenforceable PERMIT. Strictest variant; mirrors
     * {@code @PreEnforce} semantics on a per-event timeline.
     */
    TILL_DENIED,

    /**
     * The subscription stays alive across denials. Events are silently
     * dropped while the active decision is deny or PERMIT cannot be
     * enforced; emission resumes when a fresh enforceable PERMIT arrives.
     * The client cannot tell denial apart from an idle source.
     */
    DROP_WHILE_DENIED,

    /**
     * The subscription stays alive across denials, and the client is
     * informed of every transition between PENDING, PERMIT and DENY by
     * out-of-band events. Same drop-on-deny semantic for data items as
     * {@link #DROP_WHILE_DENIED}, but with explicit transition signals so
     * the client (or a UI) can distinguish "no data because denied" from
     * "no data because the source is idle."
     */
    ACCESS_AWARE
}
