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
package io.sapl.api.binary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The test vectors of the SAPL binary format, shared with {@code sapl-rust}
 * ({@code crates/sapl-api/tests/wire/cases}): a heading line {@code === ...},
 * the document, a line {@code ---}, and the expectation. Placeholders such as
 * {@code <U+00E9>} and {@code <500 times [>} are replaced in documents and in
 * expectations.
 */
@UtilityClass
class WireCases {

    /** The marker an error value is compared as. Messages of error values are not part of the vectors. */
    static final Value ANY_ERROR = Value.error("error");

    private static final String HEADING   = "=== ";
    private static final String SEPARATOR = "---";

    /** Reads the JSON notation of the vectors: numbers exactly, nesting beyond 500 levels. */
    private static final JsonMapper JSON = JsonMapper
            .builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2_000).build()).build())
            .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /**
     * One case of a case file.
     *
     * @param origin the file and the line of the heading
     * @param document the document
     * @param expectation the expectation
     */
    record Case(String origin, String document, String expectation) {
        @Override
        public String toString() {
            return origin;
        }
    }

    /** The cases of a case file in the resources of this package. */
    static List<Case> casesIn(String fileName) {
        val lines = text(fileName).split("\n", -1);
        val cases = new ArrayList<Case>();
        var index = 0;
        while (index < lines.length && !lines[index].startsWith(HEADING)) {
            index++;
        }
        val body = new ArrayList<String>();
        while (index < lines.length) {
            val heading = index;
            index++;
            body.clear();
            while (index < lines.length && !lines[index].startsWith(HEADING)) {
                body.add(lines[index++]);
            }
            val separator = body.indexOf(SEPARATOR) < 0 ? body.size() : body.indexOf(SEPARATOR);
            val origin    = fileName + ":" + (heading + 1);
            val after     = Math.min(separator + 1, body.size());
            cases.add(new Case(origin, withPlaceholdersReplaced(joined(body.subList(0, separator))),
                    withPlaceholdersReplaced(joined(body.subList(after, body.size())))));
        }
        return cases;
    }

    /** The bytes of a document in hexadecimal, with comments from {@code //} to the end of the line. */
    static byte[] bytesOfHex(String document) {
        val bytes = new ByteArrayOutputStream();
        for (val line : document.split("\n")) {
            val code = line.split("//", -1)[0].trim();
            if (!code.isEmpty()) {
                Arrays.stream(code.split("\\s+")).forEach(hex -> bytes.write(Integer.parseInt(hex, 16)));
            }
        }
        return bytes.toByteArray();
    }

    /** The value of a notation: JSON, or one of the words {@code undefined} and {@code error}. */
    static Value value(String notation) {
        return switch (notation) {
        case "undefined" -> Value.UNDEFINED;
        case "error"     -> ANY_ERROR;
        default          -> valueOf(JSON.readTree(notation));
        };
    }

    /** The value with an error in its place compared as any error. */
    static Value withAnyError(Value value) {
        return value instanceof ErrorValue ? ANY_ERROR : value;
    }

    /** The text after {@code key:} in the line of the text that starts with the key, if there is one. */
    static Optional<String> field(String text, String key) {
        return text.lines().filter(line -> line.startsWith(key + ":"))
                .map(line -> line.substring(key.length() + 1).trim()).findFirst();
    }

    /**
     * The bytes of a form an expectation states: the hexadecimal bytes after
     * {@code label:} and on the indented lines that follow, if there is one.
     */
    static Optional<byte[]> form(String expectation, String label) {
        val lines = expectation.split("\n");
        for (var i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(label + ":")) {
                val hex = new StringBuilder(lines[i].substring(label.length() + 1));
                for (var j = i + 1; j < lines.length && lines[j].startsWith(" "); j++) {
                    hex.append('\n').append(lines[j]);
                }
                return Optional.of(bytesOfHex(hex.toString()));
            }
        }
        return Optional.empty();
    }

    private static Value valueOf(JsonNode node) {
        return switch (node.getNodeType()) {
        case NULL    -> Value.NULL;
        case BOOLEAN -> Value.of(node.asBoolean());
        case NUMBER  -> Value.of(node.decimalValue());
        case STRING  -> Value.of(node.asString());
        case ARRAY   -> new ArrayValue(node.valueStream().map(WireCases::valueOf).toList());
        case OBJECT  -> {
            val builder = ObjectValue.builder();
            node.properties().forEach(entry -> builder.put(entry.getKey(), valueOf(entry.getValue())));
            yield builder.build();
        }
        default      -> throw new IllegalArgumentException("The notation " + node + " is no value.");
        };
    }

    private static String text(String fileName) {
        try (val stream = WireCases.class.getResourceAsStream(fileName)) {
            if (stream == null) {
                throw new IllegalArgumentException("No case file " + fileName + ".");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The lines without the empty ones at their end, joined by line feeds. */
    private static String joined(List<String> lines) {
        var length = lines.size();
        while (length > 0 && lines.get(length - 1).isEmpty()) {
            length--;
        }
        return String.join("\n", lines.subList(0, length));
    }

    private static String withPlaceholdersReplaced(String written) {
        val text = new StringBuilder();
        var rest = written;
        var open = rest.indexOf('<');
        while (open >= 0) {
            val close = rest.indexOf('>', open);
            text.append(rest, 0, open).append(placeholder(rest.substring(open + 1, close)));
            rest = rest.substring(close + 1);
            open = rest.indexOf('<');
        }
        return text.append(rest).toString();
    }

    private static String placeholder(String inside) {
        if (inside.startsWith("U+")) {
            return Character.toString(Integer.parseInt(inside.substring(2), 16));
        }
        val times = inside.indexOf(" times ");
        return inside.substring(times + " times ".length()).repeat(Integer.parseInt(inside.substring(0, times)));
    }
}
