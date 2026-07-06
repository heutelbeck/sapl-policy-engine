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
package io.sapl.pdp.plugins.example;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;

/**
 * Example function library contributed by the reference plugin. Depends only on
 * {@code sapl-api}.
 */
@FunctionLibrary(name = ExampleFunctionLibrary.NAME, description = "Example functions contributed by a plugin.")
public class ExampleFunctionLibrary {

    public static final String NAME = "example";

    /**
     * Counts the words in the given text. Words are non-empty sequences
     * separated by whitespace; a blank text yields zero.
     *
     * @param text the text whose words are counted
     * @return the number of words as a numeric value
     */
    @Function(docs = "```example.wordCount(TEXT text)``` Returns the number of words in the given text.")
    public Value wordCount(TextValue text) {
        var trimmed = text.value().strip();
        if (trimmed.isEmpty()) {
            return Value.of(0L);
        }
        return Value.of(trimmed.split("\\s+").length);
    }

}
