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
package io.sapl.vaadin.lsp;

import lombok.Data;

/**
 * Configuration for the SAPL LSP-based editor.
 */
@Data
public class SaplEditorLspConfiguration {

    /**
     * Autocomplete trigger modes.
     */
    public enum AutocompleteTrigger {
        /**
         * Autocomplete only triggers on Ctrl+Space (manual).
         */
        MANUAL,
        /**
         * Autocomplete triggers automatically while typing with a delay.
         */
        ON_TYPING
    }

    /**
     * Language mode: "sapl" or "sapltest".
     */
    private String language = "sapl";

    /**
     * WebSocket URL for LSP communication.
     * If null, the editor works without LSP (basic syntax highlighting only).
     */
    private String wsUrl;

    /**
     * Whether to show line numbers.
     */
    private boolean hasLineNumbers = true;

    /**
     * Whether the editor is read-only.
     */
    private boolean readOnly = false;

    /**
     * Whether to use dark theme.
     */
    private boolean darkTheme = false;

    /**
     * When autocomplete triggers. Defaults to MANUAL (Ctrl+Space only).
     */
    private AutocompleteTrigger autocompleteTrigger = AutocompleteTrigger.MANUAL;

    /**
     * Delay in milliseconds before autocomplete activates when using ON_TYPING
     * mode.
     * Defaults to 300ms. Only applies when autocompleteTrigger is ON_TYPING.
     */
    private int autocompleteDelay = 300;
}
