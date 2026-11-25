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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.functions.libraries.SchemaValidationLibrary.isCompliant;
import static io.sapl.functions.libraries.SchemaValidationLibrary.isCompliantWithExternalSchemas;
import static io.sapl.functions.libraries.SchemaValidationLibrary.validate;
import static io.sapl.functions.libraries.SchemaValidationLibrary.validateWithExternalSchemas;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Local dynamic schema references")
class SchemaLocalDynamicReferenceTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("an unresolved local dynamic target in an unused definition fails schema loading")
    void whenUnusedLocalDynamicTargetIsMissingThenEveryFunctionReturnsError(String description, String source) {
        val schema  = (ObjectValue) json(source);
        val subject = Value.of(7);

        assertThat(List.of(isCompliant(subject, schema), validate(subject, schema),
                isCompliantWithExternalSchemas(subject, schema, Value.EMPTY_ARRAY),
                validateWithExternalSchemas(subject, schema, Value.EMPTY_ARRAY)))
                .allSatisfy(result -> assertThat(result).isInstanceOf(ErrorValue.class));
    }

    static Stream<Arguments> whenUnusedLocalDynamicTargetIsMissingThenEveryFunctionReturnsError() {
        return Stream.of(arguments("missing local anchor", """
                {"$schema":"https://json-schema.org/draft/2020-12/schema",
                 "$defs":{"unused":{"$dynamicRef":"#absent"}}}
                """), arguments("missing local pointer", """
                {"$schema":"https://json-schema.org/draft/2020-12/schema",
                 "$defs":{"unused":{"$dynamicRef":"#/$defs/absent"}}}
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    @DisplayName("preparing local targets preserves their constraints and dynamic scope")
    void whenLocalDynamicTargetExistsThenItsDynamicScopeIsPreserved(String description, String source, String external,
            String input, boolean valid) {
        val schema    = (ObjectValue) json(source);
        val resources = json(external);
        val subject   = json(input);

        assertThat(isCompliantWithExternalSchemas(subject, schema, resources)).isEqualTo(Value.of(valid));
        assertThat(validateWithExternalSchemas(subject, schema, resources)).isInstanceOfSatisfying(ObjectValue.class,
                report -> assertThat(report.get("valid")).isEqualTo(Value.of(valid)));
    }

    static Stream<Arguments> whenLocalDynamicTargetExistsThenItsDynamicScopeIsPreserved() {
        val strictTree = """
                {"$id":"urn:example:strict-tree","$dynamicAnchor":"node",
                 "$ref":"urn:example:tree","unevaluatedProperties":false}
                """;
        val tree       = """
                [{"$id":"urn:example:tree","$dynamicAnchor":"node","type":"object",
                  "properties":{"data":true,"children":{"type":"array","items":{"$dynamicRef":"#node"}}}}]
                """;
        return Stream.of(arguments("unused local target is prepared without constraining the subject", """
                {"$defs":{"unused":{"$dynamicRef":"#kind"},
                          "code":{"$dynamicAnchor":"kind","type":"string"}}}
                """, "[]", "7", true), arguments("local dynamic anchor accepts text", """
                {"$dynamicRef":"#kind","$defs":{"code":{"$dynamicAnchor":"kind","type":"string"}}}
                """, "[]", "\"text\"", true), arguments("local dynamic anchor rejects a number", """
                {"$dynamicRef":"#kind","$defs":{"code":{"$dynamicAnchor":"kind","type":"string"}}}
                """, "[]", "7", false), arguments("local dynamic pointer", """
                {"$dynamicRef":"#/$defs/code","$defs":{"code":{"type":"string"}}}
                """, "[]", "\"text\"", true), arguments("local recursive anchor", """
                {"$dynamicAnchor":"node","type":"object","properties":{"next":{"$dynamicRef":"#node"}}}
                """, "[]", "{\"next\":{}}", true),
                arguments("dynamic override accepts a recursive tree", strictTree, tree,
                        "{\"data\":1,\"children\":[{\"data\":2}]}", true),
                arguments("dynamic override rejects an extra nested property", strictTree, tree,
                        "{\"data\":1,\"children\":[{\"data\":2,\"extra\":true}]}", false));
    }
}
