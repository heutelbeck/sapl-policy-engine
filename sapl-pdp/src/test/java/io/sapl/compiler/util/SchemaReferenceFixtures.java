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
package io.sapl.compiler.util;

import lombok.experimental.UtilityClass;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * References a JSON Schema may make by {@code $schema} or {@code $ref}, shared
 * by every place that loads schemas.
 */
@UtilityClass
public class SchemaReferenceFixtures {

    public static final String UNRESOLVABLE_REFERENCE = "the reference '%s' names neither a built-in meta-schema nor a provided schema";

    /**
     * @return pairs of the keyword and a reference that is neither a built-in
     * meta-schema nor a provided schema
     */
    public static Stream<Arguments> foreignReferences() {
        return Stream.of("$schema", "$ref").flatMap(keyword -> Stream
                .of("http://127.0.0.1:9/schema", "https://127.0.0.1:9/schema", "file:///etc/passwd",
                        "gopher://127.0.0.1:9/schema", "ftp://127.0.0.1:9/schema", "jar:file:///tmp/a.jar!/schema.json",
                        "classpath:draft-07/schema", "resource:draft-07/schema", "urn:example:unknown",
                        "https://json-schema.org/jsv-messages.properties", "https://json-schema.org/missing")
                .map(reference -> arguments(keyword, reference)));
    }

    /**
     * @return a description, a schema, the provided schemas and the reference
     * the schema is refused for, which is neither a built-in meta-schema nor a
     * provided schema, however the schema names it
     */
    public static Stream<Arguments> foreignReferenceSchemas() {
        return Stream.of(arguments("a relative reference", """
                {"$ref": "other.json"}
                """, "[]", "mem://inline/other.json"), arguments("a relative reference to a parent", """
                {"$ref": "../other.json"}
                """, "[]", "mem://inline/other.json"), arguments("an absolute path", """
                {"$ref": "/etc/passwd"}
                """, "[]", "mem://inline/etc/passwd"), arguments("a reference relative to the $id of the schema", """
                {"$id": "http://127.0.0.1:9/base/root", "$ref": "other"}
                """, "[]", "http://127.0.0.1:9/base/other"), arguments("a reference of a property", """
                {"properties": {"a": {"$ref": "http://127.0.0.1:9/schema"}}}
                """, "[]", "http://127.0.0.1:9/schema"), arguments("a dynamic reference", """
                {"$dynamicRef": "http://127.0.0.1:9/schema"}
                """, "[]", "http://127.0.0.1:9/schema"), arguments("a reference of a provided schema", """
                {"$ref": "urn:example:provided"}
                """, """
                [{"$id": "urn:example:provided", "$ref": "http://127.0.0.1:9/schema"}]
                """, "http://127.0.0.1:9/schema"), arguments("the dialect of a provided schema", """
                {"$ref": "urn:example:provided"}
                """, """
                [{"$id": "urn:example:provided", "$schema": "http://127.0.0.1:9/schema"}]
                """, "http://127.0.0.1:9/schema"));
    }

    /**
     * @return a description, a schema naming a built-in meta-schema, a subject
     * compliant with it and a subject that is not
     */
    public static Stream<Arguments> builtInReferences() {
        return Stream.of(arguments("draft-04 by $schema", """
                {"$schema": "http://json-schema.org/draft-04/schema#", "type": "string", "minLength": 2}
                """, "\"ab\"", "\"a\""), arguments("draft-06 by $schema", """
                {"$schema": "http://json-schema.org/draft-06/schema#", "type": "string", "minLength": 2}
                """, "\"ab\"", "\"a\""), arguments("draft-07 by $schema", """
                {"$schema": "http://json-schema.org/draft-07/schema#", "type": "string", "minLength": 2}
                """, "\"ab\"", "\"a\""), arguments("draft 2019-09 by $schema", """
                {"$schema": "https://json-schema.org/draft/2019-09/schema", "type": "string", "minLength": 2}
                """, "\"ab\"", "\"a\""), arguments("draft 2020-12 by $schema", """
                {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "string", "minLength": 2}
                """, "\"ab\"", "\"a\""), arguments("the draft-07 meta-schema by $ref", """
                {"$ref": "http://json-schema.org/draft-07/schema#"}
                """, "{\"type\": \"string\"}", "{\"minLength\": -1}"),
                arguments("the draft 2020-12 meta-schema by $ref", """
                        {"$ref": "https://json-schema.org/draft/2020-12/schema"}
                        """, "{\"type\": \"string\"}", "{\"type\": 5}"));
    }
}
