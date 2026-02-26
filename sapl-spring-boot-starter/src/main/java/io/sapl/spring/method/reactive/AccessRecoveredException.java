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
package io.sapl.spring.method.reactive;

import io.sapl.api.SaplVersion;

import java.io.Serial;

/**
 * Signal exception indicating that access has been recovered after a previous
 * deny. Emitted by the {@code EnforceRecoverableIfDeniedPolicyEnforcementPoint}
 * when {@code signalAccessRecovery} is enabled and a DENY-to-PERMIT transition
 * occurs.
 * <p>
 * This is not an error in the traditional sense but a control-flow signal
 * delivered through the {@code onErrorContinue} mechanism so that subscribers
 * can distinguish "access restored, source idle" from "still denied."
 * <p>
 * Subscribers handle this alongside {@code AccessDeniedException}:
 *
 * <pre>{@code
 * flux.onErrorContinue((error, value) -> {
 *     if (error instanceof AccessDeniedException)
 *         showNoAccess();
 *     if (error instanceof AccessRecoveredException)
 *         showReady();
 * }).subscribe(data -> display(data));
 * }</pre>
 */
public class AccessRecoveredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a new {@code AccessRecoveredException} with the given message.
     *
     * @param message the detail message
     */
    public AccessRecoveredException(String message) {
        super(message);
    }

}
