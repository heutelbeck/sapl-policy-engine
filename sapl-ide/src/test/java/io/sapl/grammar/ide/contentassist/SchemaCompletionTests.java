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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tests regarding the autocompletion of schema statements
 */
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
class SchemaCompletionTests extends CompletionTests {

    final List<String> environmentVariableNames = List.of("calendar_schema");

    /**
     * Tests regarding the preamble
     */

    @Test
    void testCompletion_Preamble_ReturnsSchemaKeyword_For_Subject() {
        final var document = "subject §";
        final var expected = List.of("schema");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_Preamble_PartialSchemaKeyword_ReturnsSchemaKeyword() {
        final var document = "subject schem§";
        final var expected = List.of("schema");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_Preamble_SchemaNameIsEmptyString_ReturnsEnvironmentVariables() {
        final var document = "subject schema c§";
        final var expected = environmentVariableNames;
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_Preamble_SubscriptionElementIsAuthzElement() {
        final var document = "§ schema \"test\"";
        final var expected = List.of("subject", "action", "resource", "environment");
        assertProposalsContain(document, expected);
    }

    /**
     * Tests regarding variable annotation in policy body
     */

    @Test
    void testCompletion_PolicyBody_SchemaAnnotation() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = 1 s§""";
        final var expected = List.of("schema");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_emptySchema() {
        final var document = "policy \"test\" deny where var foo = 1 schema {}; fo§";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_InvalidSchema() {
        final var document = "policy \"test\" permit where var foo = \"test\" schema {;!}; fo§";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_getSchemaFromJSONinText() {
        final var document = """
                policy "test" deny where var foo = 1 schema {
                  "properties": {
                    "name": {
                      "type": "object",
                      "properties": {
                        "firstname": {"type": "string"}
                      }
                    },
                    "age": {"type": "number"}
                  }
                };
                foo§""";

        final var expected = List.of(".age", ".name", ".name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_suggestSchemaPathsAfterDot() {
        final var document = """
                policy "test" deny where var foo = 1 schema {
                  "properties": {
                    "name": {
                      "type": "object",
                      "properties": {
                        "firstname": {"type": "string"}
                      }
                    },
                    "age": {"type": "number"}
                  }
                };
                foo.§""";
        final var expected = List.of(".age", ".name", ".name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_getNestedSchemaFromEnvironmentVariable() {
        final var document = """
                policy "test"
                permit
                where
                  var bar = 5;
                  var foo = "test" schema schema_with_additional_keywords;
                  foo.§""";
        final var expected = List.of(".subject.age", ".subject.name", ".subject.name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_getNestedSchemaFromEnvironmentVariable2() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = "test" schema schema_with_additional_keywords;
                  foo§
                  var foobar = 1;""";
        final var expected = List.of(".subject.age", ".subject.name", ".subject.name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_SchemaNotInEnvironmentVariable() {
        final var document = """
                policy "test"
                permit
                where
                  var foo = "test" schema non_existent_schema;
                  fo§""";
        final var expected = List.of("foo");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_NotSuggestEnumKeywords() {
        final var document = """
                policy "test"
                permit
                where
                  final var bar = 3;
                  final var foo = "test" schema
                                {
                                   "type": "object",
                                   "properties": {
                                     	"java": {
                                     		"type": "object",
                                     		"properties": {
                                     			"name": {
                                     				"type": "string",
                                     				"enum": ["registerNewCustomer",
                                     				         "changeAddress"]
                                     			}
                                     		}
                                     }
                                   }
                                 };
                 foo§""";
        final var expected = List.of(".java", ".java.name");
        final var unwanted = List.of(".name.enum[0]", ".name.enum[1]");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_SuggestArrayItems() {
        final var document = """
                policy "test"
                permit
                where
                  var bar = 3;
                  var foo = "test" schema {
                                             "type": "array",
                                             "items": [
                                               { "type": "string" },
                                               { "enum": ["Street", "Avenue"] }
                                             ]
                                          };
                  foo§""";
        final var expected = List.of("foo[]");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestPDPScopedVariable_NotSuggestOutOfScopeVariable() {
        final var document = "subject schema §policy \"test\" permit where var foo = 5;";
        final var expected = environmentVariableNames;
        final var unwanted = List.of("foo");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementSubject() {
        final var document = "subject schema general_schema policy \"test\" permit where subject§";
        final var expected = List.of(".age", ".name", ".name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementAction() {
        final var document = "action schema general_schema policy \"test\" permit where action§";
        final var expected = List.of(".age", ".name", ".name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElement_with_idsteps() {
        final var document = "subject schema general_schema policy \"test\" permit where subject.name§";
        final var expected = List.of(".firstname", "name.firstname");
        final var unwanted = List.of("var", "filter.blacken");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElement_with_idsteps_with_trailing_dot() {
        final var document = "subject schema general_schema policy \"test\" permit where subject.name.§";
        final var expected = List.of(".firstname");
        final var unwanted = List.of("var", "filter.blacken");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElement_with_incomplete_id_step() {
        final var document = "subject schema general_schema policy \"test\" permit where subject.a§";
        final var expected = List.of("age");
        final var unwanted = List.of("var", "filter.blacken");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_getFromJSONinText_for_AuthzElementSubject() {
        final var document = """
                subject schema {
                "properties":
                    {"name": {"type": "object", "properties": {"firstname": {"type": "string"}}},
                     "age": {"type": "number"}}
                    }
                policy "test" deny where subject§
                """;
        final var expected = List.of(".age", ".name", ".name.firstname");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_recursive_schema() {
        final var document = """
                subject schema
                 {
                   "type": "object",
                   "properties": {
                     "name": { "type": "string" },
                     "children": {
                       "type": "array",
                       "items": { "$ref": "#" }
                     }
                   }
                 }
                 policy "test" deny where subject§""";
        final var expected = List.of(".children", ".children[]", ".children[].children", ".children[].children[]",
                ".children[].children[].children", ".children[].children[].children[]",
                ".children[].children[].children[].children", ".children[].children[].children[].children[]",
                ".children[].children[].children[].children[].children",
                ".children[].children[].children[].children[].children[]",
                ".children[].children[].children[].children[].name", ".children[].children[].children[].name",
                ".children[].children[].name", ".children[].name", ".name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_array_with_empty_items() {
        final var document = """
                subject schema
                 {
                   "type": "object",
                   "properties": {
                     "name": {
                       "type": "array"
                     }
                   }
                 }
                 policy "test" deny where subject§""";
        final var expected = List.of(".name", ".name[]");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_resolveInternalReferences() {
        final var document = """
                subject schema
                 {
                    "type": "object",
                    "properties": {
                      "name": { "$ref": "#/$defs/name" }
                    },
                    "$defs": {
                      "name": {"type": "object", "properties": {"first_name": {"type": "string"},
                      "last_name": {"type": "string"}}
                    }
                  }
                 policy "test" deny where subject.§""";
        final var expected = List.of(".name", ".name.first_name", ".name.last_name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferences() {
        final var document = """
                subject schema
                 {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "shipping_address": { "$ref": "https://example.com/address.schema.json" }
                     }
                  }
                 policy "test" deny where subject§""";
        final var expected = List.of(".name", ".shipping_address", ".shipping_address.country-name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferenceCombinedWithInternalReference() {
        final var document = """
                subject schema
                 {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "place_of_birth": { "$ref": "https://example.com/calendar.schema.json#/properties/geo" }
                     }
                  }
                 policy "test" deny where subject§""";
        final var expected = List.of(".name", ".place_of_birth");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferenceToNonExistingSchema() {
        final var document = """
                subject schema
                 {
                    "type": "object",
                    "properties": {
                      "place_of_birth": { "$ref": "notexisting_schema" }
                     }
                  }
                 policy "test" deny where subject§""";
        final var expected = List.of(".place_of_birth");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferenceToNonExistingSchemaWithInternalReference() {
        final var document = """
                subject schema
                 {
                    "type": "object",
                    "properties": {
                      "place_of_birth": { "$ref": "notexisting_schema/#/geo" }
                     }
                  }
                 policy "test" deny where subject§""";
        final var expected = List.of(".place_of_birth");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_missingChildAttribute() {
        final var document = """
                policy "test" deny where var foo = 1 schema {
                  "properties": {
                    "name": {
                      }
                    }
                  }
                };
                fo§""";
        final var expected = List.of("foo", "foo.name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_patternProperties() {
        final var document = """
                policy "test" deny where var foo = 1 schema
                {
                  "type": "object",
                  "patternProperties": {
                    "^S_": { "type": "string" },
                    "^I_": { "type": "integer" }
                  }
                };
                fo§""";
        final var expected = List.of("foo");
        final var unwanted = List.of("foo.patternProperties", "foo.patternProperties.^S_");
        assertProposalsContainWantedAndDoNotContainUnwanted(document, expected, unwanted);
    }

    @Test
    void testCompletion_PolicyBody_function_without_import() {
        final var document = """
                policy "test" deny where var foo = schemaTest.person();
                fo§""";
        final var expected = List.of("foo", "foo.name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_with_wildcard_import() {
        final var document = """
                import schemaTest.*
                policy "test" deny where var foo = person();
                fo§""";
        final var expected = List.of("foo", "foo.name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import() {
        final var document = """
                import schemaTest.person as xyz
                policy "test" deny where var foo = xyz();
                fo§""";
        final var expected = List.of("foo", "foo.name");
        assertProposalsContain(document, expected);
    }

    @Test
    void testCompletion_proposal_contains_space() {
        final var document = """
                subject schema {
                "properties":
                    {
                     "first name": {"type": "string"}}
                    }
                policy "test" deny where subject§
                """;
        final var expected = List.of(".'first name'");
        assertProposalsContain(document, expected);
    }

}
