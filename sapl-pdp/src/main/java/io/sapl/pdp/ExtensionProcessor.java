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
package io.sapl.pdp;

import java.util.Map;

import io.sapl.api.model.Value;

/**
 * A consumer of bundle-carried extension data, wired into the PDP through
 * {@link PolicyDecisionPointBuilder#withExtensionProcessor}. It lets a component
 * other than the PDP, for example a gateway configuring upstream connections,
 * react to configuration changes.
 * <p>
 * The processor receives extension data already decrypted. Secret sealing and
 * unsealing happen at the PDP layer before the processor is called, so both the
 * cleartext {@link #onLoad} extensions and the extension secrets arrive as plain
 * {@link Value}s. The processor never handles ciphertext or sealing keys.
 * <p>
 * A processor is notified after the PDP has applied the configuration, so
 * policies are live before the side effects a processor sets up. Notifications
 * are isolated: a processor that throws is logged and does not affect the PDP or
 * other processors. This listener contract is the simple form. A future
 * transactional form will add a prepare and commit phase so a configuration only
 * goes live when both the policies compile and every processor accepts its
 * slice.
 */
public interface ExtensionProcessor {

    /**
     * Notifies the processor that a configuration was loaded for a pdpId.
     *
     * @param pdpId the identity of the loaded configuration
     * @param extensions the cleartext extension data, keyed by extension name
     * @param extensionSecrets the unsealed extension secrets, keyed by extension
     * name
     */
    void onLoad(String pdpId, Map<String, Value> extensions, Map<String, Value> extensionSecrets);

    /**
     * Notifies the processor that a pdpId's configuration was removed, so the
     * processor can tear down anything it set up for it.
     *
     * @param pdpId the identity of the removed configuration
     */
    void onRemove(String pdpId);

}
