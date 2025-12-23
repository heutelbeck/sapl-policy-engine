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
package io.sapl.compiler;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ValueMetadata;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Utility class for creating ErrorValue instances with source location
 * information
 * from ANTLR parse tree contexts.
 */
@UtilityClass
public class Error {

    /**
     * Creates an ErrorValue with source location extracted from an ANTLR parse tree
     * context.
     *
     * @param context the parse tree context for source location (may be null)
     * @param metadata the value metadata
     * @param message the error message format string
     * @param args format arguments for the message
     * @return an ErrorValue with the formatted message and source location
     */
    public ErrorValue at(ParserRuleContext context, ValueMetadata metadata, @NonNull String message, Object... args) {
        return new ErrorValue(String.format(message, args), null, metadata, SourceLocationUtil.fromContext(context));
    }

    /**
     * Creates an ErrorValue with source location but empty metadata.
     *
     * @param context the parse tree context for source location (may be null)
     * @param message the error message format string
     * @param args format arguments for the message
     * @return an ErrorValue with the formatted message and source location
     */
    public ErrorValue at(ParserRuleContext context, @NonNull String message, Object... args) {
        return new ErrorValue(String.format(message, args), null, ValueMetadata.EMPTY,
                SourceLocationUtil.fromContext(context));
    }

    /**
     * Creates an ErrorValue without source location information.
     *
     * @param message the error message format string
     * @param args format arguments for the message
     * @return an ErrorValue with the formatted message
     */
    public ErrorValue create(@NonNull String message, Object... args) {
        return new ErrorValue(String.format(message, args), null, ValueMetadata.EMPTY, null);
    }

}
