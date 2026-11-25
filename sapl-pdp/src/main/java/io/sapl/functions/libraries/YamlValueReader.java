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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.resolver.CoreScalarResolver;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

/** Reads the YAML core types that have a SAPL value and resolves aliases to immutable values. */
final class YamlValueReader {
    private static final String ERROR_DEPTH                          = "The YAML collection exceeds the maximum nesting depth of %d";
    private static final String ERROR_KEY                            = "A YAML object key must be a scalar";
    private static final String ERROR_MAXIMUM_NESTING_DEPTH_EXCEEDED = "Maximum nesting depth exceeded.";
    private static final String ERROR_MISSING                        = "Unknown JsonNode type: MISSING.";
    private static final String ERROR_NONFINITE                      = "YAML infinity and NaN are no numbers";
    private static final String ERROR_NUMBER                         = "The YAML number exceeds the maximum length of %d characters";
    private static final String ERROR_RECURSIVE                      = "A YAML alias refers to a recursive collection";
    private static final String ERROR_SCALAR                         = "Invalid value '%s' for YAML tag '%s'";
    private static final String ERROR_TAG                            = "Unsupported YAML tag '%s'";
    private static final String ERROR_TAG_KIND                       = "YAML tag '%s' does not match its node";

    private static final int            MAX_DEPTH    = TextParseLimits.STREAM_READ_CONSTRAINTS.getMaxNestingDepth();
    private static final int            MAX_NUMBER   = TextParseLimits.STREAM_READ_CONSTRAINTS.getMaxNumberLength();
    private static final ScalarResolver CORE_SCALARS = coreScalars();
    private static final LoadSettings   SETTINGS     = LoadSettings.builder()
            .setCodePointLimit(TextParseLimits.MAX_INPUT_CHARS).setMaxAliasesForCollections(50)
            .setAllowNonScalarKeys(false).setSchema(new CoreSchema() {
                                                                 @Override
                                                                 public ScalarResolver getScalarResolver() {
                                                                     return CORE_SCALARS;
                                                                 }
                                                             })
            .build();

    private static ScalarResolver coreScalars() {
        val core = new CoreScalarResolver(false);
        core.addImplicitResolver(Tag.NULL, CoreScalarResolver.NULL, "N~");
        // SAPL reads environment-variable expressions as ordinary text.
        return (value, plain) -> value.startsWith("$") ? Tag.STR : core.resolve(value, plain);
    }

    private final IdentityHashMap<Node, Decoded> anchors = new IdentityHashMap<>();

    private record Decoded(Value value, int depth) {}

    private YamlValueReader() {
    }

    static Value read(String yaml) {
        // Bound the source nesting before the composer constructs its representation graph.
        val parser = new ParserImpl(SETTINGS, new StreamReader(SETTINGS, yaml)) {
                       private int depth;

                       @Override
                       public Event next() {
                           val event = super.next();
                           switch (event.getEventId()) {
                           case MappingStart, SequenceStart -> checkDepth(++depth);
                           case MappingEnd, SequenceEnd     -> depth--;
                           default                          -> {}
                           }
                           return event;
                       }
                   };
        val root   = new Composer(SETTINGS, parser).getSingleNode();
        if (root.isEmpty()) {
            return Value.error(ERROR_MISSING);
        }
        val decoded = new YamlValueReader().decode(root.get());
        return decoded.depth() >= ValueJsonMarshaller.MAX_DEPTH ? Value.error(ERROR_MAXIMUM_NESTING_DEPTH_EXCEEDED)
                : decoded.value();
    }

    private static void checkDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new YamlEngineException(ERROR_DEPTH.formatted(MAX_DEPTH));
        }
    }

    private Decoded decode(Node node) {
        if (node.isRecursive()) {
            throw new YamlEngineException(ERROR_RECURSIVE);
        }
        val retained = anchors.get(node);
        if (retained != null) {
            return retained;
        }
        val decoded = switch (node) {
        case ScalarNode scalar                                            -> new Decoded(scalarValue(scalar), 0);
        case SequenceNode sequence when Tag.SEQ.equals(sequence.getTag()) -> readSequence(sequence);
        case MappingNode mapping when Tag.MAP.equals(mapping.getTag())    -> readMapping(mapping);
        default                                                           -> throw tagMismatch(node.getTag());
        };
        if (node.getAnchor().isPresent()) {
            anchors.put(node, decoded);
        }
        return decoded;
    }

    private Decoded readSequence(SequenceNode sequence) {
        val values = new ArrayList<Value>(sequence.getValue().size());
        int depth  = 0;
        for (val child : sequence.getValue()) {
            val entry = decode(child);
            values.add(entry.value());
            depth = Math.max(depth, entry.depth());
        }
        return new Decoded(Value.ofArray(values), depth + 1);
    }

    private Decoded readMapping(MappingNode mapping) {
        val object           = ObjectValue.builder();
        val collectionDepths = new HashMap<String, Integer>();
        for (val pair : mapping.getValue()) {
            if (!(pair.getKeyNode() instanceof ScalarNode key)) {
                throw new YamlEngineException(ERROR_KEY);
            }
            decode(key);
            val entry = decode(pair.getValueNode());
            val name  = key.getValue();
            object.put(name, entry.value());
            // Repeated keys keep their last value, including its depth.
            if (entry.depth() == 0) {
                collectionDepths.remove(name);
            } else {
                collectionDepths.put(name, entry.depth());
            }
        }
        int depth = 0;
        for (val childDepth : collectionDepths.values()) {
            depth = Math.max(depth, childDepth);
        }
        return new Decoded(object.build(), depth + 1);
    }

    private static Value scalarValue(ScalarNode scalar) {
        val tag  = scalar.getTag();
        val text = scalar.getValue();
        return switch (tag.getValue()) {
        case Tag.PREFIX + "str"                       -> Value.of(text);
        case Tag.PREFIX + "null"                      -> {
            // SnakeYAML also matches a space, which is not a YAML Core null spelling.
            if (!text.isEmpty() && (" ".equals(text) || !CoreScalarResolver.NULL.matcher(text).matches())) {
                throw new YamlEngineException(
                        ERROR_SCALAR.formatted(text, tag.getValue().substring(Tag.PREFIX.length())));
            }
            yield Value.NULL;
        }
        case Tag.PREFIX + "bool"                      -> {
            if (!CoreScalarResolver.BOOL.matcher(text).matches()) {
                throw new YamlEngineException(
                        ERROR_SCALAR.formatted(text, tag.getValue().substring(Tag.PREFIX.length())));
            }
            yield Value.of(Boolean.parseBoolean(text));
        }
        case Tag.PREFIX + "int", Tag.PREFIX + "float" -> number(tag, text);
        default                                       -> throw new YamlEngineException(ERROR_TAG.formatted(tag));
        };
    }

    private static YamlEngineException tagMismatch(Tag tag) {
        return switch (tag.getValue()) {
        case Tag.PREFIX + "str", Tag.PREFIX + "null", Tag.PREFIX + "bool", Tag.PREFIX + "int", Tag.PREFIX + "float",
                Tag.PREFIX + "map",
                Tag.PREFIX
                        + "seq"                                                                                                                             ->
            new YamlEngineException(ERROR_TAG_KIND.formatted(tag.getValue().substring(Tag.PREFIX.length())));
        default                                                                                                                                             ->
            new YamlEngineException(ERROR_TAG.formatted(tag));
        };
    }

    private static Value number(Tag tag, String text) {
        if (text.length() > MAX_NUMBER) {
            throw new YamlEngineException(ERROR_NUMBER.formatted(MAX_NUMBER));
        }
        if (Tag.INT.equals(tag)) {
            if (!CoreScalarResolver.INT.matcher(text).matches()) {
                throw new YamlEngineException(
                        ERROR_SCALAR.formatted(text, tag.getValue().substring(Tag.PREFIX.length())));
            }
            if (text.startsWith("0x") || text.startsWith("0o")) {
                val base = text.startsWith("0x") ? 16 : 8;
                return Value.of(new BigDecimal(new BigInteger(text.substring(2), base)));
            }
        } else {
            if (!CoreScalarResolver.FLOAT.matcher(text).matches()) {
                throw new YamlEngineException(
                        ERROR_SCALAR.formatted(text, tag.getValue().substring(Tag.PREFIX.length())));
            }
            // The remaining spellings in the core float grammar are .inf and .nan.
            val last = Character.toLowerCase(text.charAt(text.length() - 1));
            if (last == 'f' || last == 'n') {
                throw new YamlEngineException(ERROR_NONFINITE);
            }
        }
        return Value.of(new BigDecimal(text));
    }
}
