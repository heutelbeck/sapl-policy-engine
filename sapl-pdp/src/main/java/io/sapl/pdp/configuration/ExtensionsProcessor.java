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
package io.sapl.pdp.configuration;

import io.sapl.api.pdp.configuration.PDPConfiguration;

/**
 * The runtime-environment component through which a configuration's
 * bundle-carried extension data is deployed. It is not part of the policy
 * decision path, the compiled voter never sees it, it is a wiring concern of the
 * host, for example a gateway configuring upstream connections. How the host
 * organises its capabilities, and how it loads, updates, or unloads them, is
 * entirely behind this interface, the PDP never sees individual capabilities.
 * <p>
 * Extension data arrives already unsealed, secrets are decrypted before the
 * processor is called, so cleartext extensions and extension secrets are read
 * from the configuration and the processor never handles ciphertext or keys.
 * <p>
 * A configuration goes live transactionally, in two phases:
 * <ol>
 * <li>{@link #prepare} validates the configuration's extension slice against the
 * host's local capabilities and confirms every capability the configuration
 * declares critical can be deployed. It must be side-effect-free, returning
 * {@code false} aborts the whole update and the last-good configuration is
 * kept.</li>
 * <li>{@link #commit} is called only after the policies compiled and
 * {@code prepare} accepted. The host then deploys the extension data for the
 * pdpId, reconciling its capabilities internally (load new, update changed,
 * unload gone). It owns the runtime lifecycle and failure state, a capability
 * that fails to load surfaces at policy evaluation as an error value, not as a
 * configuration failure.</li>
 * </ol>
 * {@link #remove} tears down everything held for a pdpId whose configuration was
 * removed or expired.
 */
public interface ExtensionsProcessor {

    /**
     * Validates the configuration's extension slice against the host's local
     * capabilities. Side-effect-free. Returning {@code false} aborts the update
     * and the last-good configuration for the pdpId is kept.
     *
     * @param pdpId the identity of the configuration
     * @param configuration the configuration whose extension data to validate
     * @return {@code true} if the host accepts the extension slice, including
     * being able to deploy every declared critical capability
     */
    boolean prepare(String pdpId, PDPConfiguration configuration);

    /**
     * Deploys the configuration's extension data for the pdpId. Called only after
     * the policies compiled and {@link #prepare} accepted. The host reconciles its
     * capabilities internally and owns their runtime lifecycle. Implementations do
     * not throw, a load failure is the host's own state, surfaced at evaluation as
     * an error value.
     *
     * @param pdpId the identity of the configuration
     * @param configuration the configuration whose extension data to deploy
     */
    void commit(String pdpId, PDPConfiguration configuration);

    /**
     * Tears down everything the host holds for a pdpId whose configuration was
     * removed or expired.
     *
     * @param pdpId the identity of the removed configuration
     */
    void remove(String pdpId);

}
