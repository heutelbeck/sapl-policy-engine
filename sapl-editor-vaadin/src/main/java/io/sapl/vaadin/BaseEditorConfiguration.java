/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.vaadin;

import lombok.Data;

/**
 * Base configuration object to initialize the editor.
 */
@Data
public class BaseEditorConfiguration {
    private boolean hasLineNumbers    = true;
    private boolean autoCloseBrackets = true;
    private boolean matchBrackets     = true;
    private int     textUpdateDelay   = 500;
    private boolean readOnly          = false;
    private boolean lint              = true;
    private boolean darkTheme         = false;
}
