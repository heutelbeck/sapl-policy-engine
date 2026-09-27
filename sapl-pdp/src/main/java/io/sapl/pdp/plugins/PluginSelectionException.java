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
package io.sapl.pdp.plugins;

import io.sapl.api.SaplVersion;
import lombok.experimental.StandardException;

import java.io.Serial;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Thrown when the plugins a PDP configuration requests cannot be provided for
 * that PDP: requested plugins are not available, or their contributions cannot
 * be combined. This is not a policy compilation error, so it deliberately does
 * not extend {@code SaplCompilerException}; the configuration source converts
 * it into a compilation failure of the affected PDP only.
 *
 * @since 4.2.0
 */
@StandardException
public class PluginSelectionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String ERROR_PLUGINS_UNAVAILABLE = "Plugins requested by PDP '%s' are not available: %s";

    /**
     * Creates the exception for requested plugins that are not available. The
     * message is built here, so every source of plugins reports missing plugins
     * identically, without distinguishing the cause (not installed, failed to
     * load, rejected).
     *
     * @param pdpId the PDP whose configuration requested the plugins
     * @param missing the ids of the requested plugins that are not available,
     * reported in sorted order
     */
    public PluginSelectionException(String pdpId, Collection<String> missing) {
        super(ERROR_PLUGINS_UNAVAILABLE.formatted(pdpId, new TreeSet<>(missing)));
    }
}
