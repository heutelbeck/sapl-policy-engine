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
package io.sapl.spring.pep.streaming;

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.Getter;

import java.io.Serial;

/**
 * Signal emitted by the streaming PEP when the subscription enters
 * permitting state and {@code signalTransitions} is enabled on the
 * annotation. Fires symmetrically on initial grant (Pending -&gt;
 * Permitting) and resume (Suspended -&gt; Permitting). Carried on the
 * error channel as a non-terminal signal: subscribers consume it via
 * {@code onErrorContinue} (typically through {@link TransitionSignals})
 * and the stream continues emitting items.
 * <p>
 * Distinct from
 * {@link org.springframework.security.access.AccessDeniedException}
 * by design. A naive subscriber that does not opt into the
 * transition-signalling contract would not expect a grant signal;
 * routing it through a separate exception type makes the contract
 * explicit and lets subscribers pattern-match without inspecting
 * payloads.
 *
 * @since 4.1.0
 */
public final class AccessGrantedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    @Getter
    private final AuthorizationDecision decision;

    /**
     * @param decision the PERMIT decision that established access; the
     * subscriber may inspect it to apply decision-specific logic
     */
    public AccessGrantedException(AuthorizationDecision decision) {
        super("Access granted.");
        this.decision = decision;
    }
}
