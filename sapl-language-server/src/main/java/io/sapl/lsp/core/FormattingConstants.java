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
package io.sapl.lsp.core;

import lombok.experimental.UtilityClass;

/**
 * Shared formatting constants for SAPL and SAPLTest formatting providers.
 */
@UtilityClass
public class FormattingConstants {

    /**
     * Number of spaces per indentation level.
     */
    public static final int INDENT_SIZE = 4;

    /**
     * Indentation string (4 spaces).
     */
    public static final String INDENT = "    ";

    /**
     * Soft line width limit.
     */
    public static final int LINE_WIDTH = 120;

}
