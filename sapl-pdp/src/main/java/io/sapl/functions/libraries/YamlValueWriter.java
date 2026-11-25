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
package io.sapl.functions.libraries;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.NumberValueLimits;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;

import java.io.IOException;
import java.io.Writer;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/** Writes SAPL values as block YAML directly into the destination's output allowance. */
final class YamlValueWriter {
    private static final String ERROR_DEPTH     = "Maximum nesting depth exceeded.";
    private static final String ERROR_NULL      = "Cannot marshall null to JsonNode.";
    private static final String ERROR_UNDEFINED = "Cannot marshall UndefinedValue to JSON.";
    private static final String ERROR_VALUE     = "Cannot marshall ErrorValue to JSON: %s%s";

    private static final String      INDENT     = "  ".repeat(ValueJsonMarshaller.MAX_DEPTH);
    private static final Pattern     IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> RESERVED   = Set.of("y", "Y", "n", "N", "yes", "Yes", "YES", "no", "No", "NO",
            "on", "On", "ON", "off", "Off", "OFF", "true", "True", "TRUE", "false", "False", "FALSE", "null", "Null",
            "NULL");

    private YamlValueWriter() {
    }

    static String write(Value value) throws IOException {
        return TextOutputLimits.write(output -> writeNode(output, value, 0));
    }

    private static void writeNode(Writer output, Value value, int depth) throws IOException {
        val collection = value instanceof ObjectValue || value instanceof ArrayValue;
        if (depth >= ValueJsonMarshaller.MAX_DEPTH || (collection && depth + 1 >= ValueJsonMarshaller.MAX_DEPTH)) {
            throw new IllegalArgumentException(ERROR_DEPTH);
        }
        if (value instanceof ObjectValue object && !object.isEmpty()) {
            if (depth > 0) {
                output.write('\n');
            }
            for (val entry : object.entrySet()) {
                output.write(INDENT, 0, 2 * depth);
                val key = entry.getKey();
                if (IDENTIFIER.matcher(key).matches() && !RESERVED.contains(key)) {
                    output.write(key);
                } else {
                    writeQuoted(output, key);
                }
                output.write(':');
                writeNode(output, entry.getValue(), depth + 1);
            }
        } else if (value instanceof ArrayValue array && !array.isEmpty()) {
            if (depth > 0) {
                output.write('\n');
            }
            for (val item : array) {
                output.write(INDENT, 0, 2 * depth);
                output.write('-');
                writeNode(output, item, depth + 1);
            }
        } else {
            if (depth > 0) {
                output.write(' ');
            }
            switch (value) {
            case null                       -> throw new IllegalArgumentException(ERROR_NULL);
            case ErrorValue error           -> throw new IllegalArgumentException(
                    ERROR_VALUE.formatted(error.message(), error.message().endsWith(".") ? "" : "."));
            case UndefinedValue ignored     -> throw new IllegalArgumentException(ERROR_UNDEFINED);
            case TextValue(String text)     -> writeQuoted(output, text);
            case NullValue ignored          -> output.write("null");
            case BooleanValue(boolean flag) -> output.write(Boolean.toString(flag));
            case NumberValue number         -> output.write(NumberValueLimits.wireNumber(number.value()).toString());
            case ArrayValue ignored         -> output.write("[]");
            case ObjectValue ignored        -> output.write("{}");
            }
            output.write('\n');
        }
    }

    private static void writeQuoted(Writer output, String text) throws IOException {
        output.write('"');
        int start = 0;
        for (var offset = 0; offset < text.length(); offset++) {
            val c      = text.charAt(offset);
            val escape = switch (c) {
                       case '"'  -> "\\\"";
                       case '\\' -> "\\\\";
                       case '\b' -> "\\b";
                       case '\t' -> "\\t";
                       case '\n' -> "\\n";
                       case '\f' -> "\\f";
                       case '\r' -> "\\r";
                       default   -> (c < 0x20 || (c >= 0x7F && c <= 0x9F && c != 0x85) || c >= 0xFFFE)
                               ? "\\u" + HexFormat.of().toHexDigits(c)
                               : null;
                       };
            if (escape != null) {
                output.write(text, start, offset - start);
                output.write(escape);
                start = offset + 1;
            }
        }
        output.write(text, start, text.length() - start);
        output.write('"');
    }
}
