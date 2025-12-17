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
package io.sapl.lsp.server;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

import io.sapl.lsp.configuration.ConfigurationManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles workspace operations for the Language Server.
 */
@Slf4j
public class SAPLWorkspaceService implements WorkspaceService {

    private final ConfigurationManager configurationManager;

    /**
     * Creates a new workspace service.
     *
     * @param configurationManager the configuration manager
     */
    public SAPLWorkspaceService(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        log.debug("Configuration changed: {}", params.getSettings());
        // Custom ConfigurationProvider implementations handle their own cache
        // invalidation
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        log.debug("Watched files changed: {}", params.getChanges());
        // Handle file system changes if needed
    }

}
