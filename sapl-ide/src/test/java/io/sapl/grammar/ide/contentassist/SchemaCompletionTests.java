/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
        testCompletion(it -> {
            final var policy = "subject ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schema");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_Preamble_PartialSchemaKeyword_ReturnsSchemaKeyword() {
        testCompletion(it -> {
            final var policy = "subject schem";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schema");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_Preamble_SchemaNameIsEmptyString_ReturnsEnvironmentVariables() {
        testCompletion(it -> {
            final var policy = "subject schema ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = environmentVariableNames;
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_Preamble_SubscriptionElementIsAuthzElement() {
        testCompletion(it -> {
            final var policy = " schema \"test\"";
            it.setModel(policy);
            it.setColumn(0);
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "action", "resource", "environment");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    /**
     * Tests regarding variable annotation in policy body
     */

    @Test
    void testCompletion_PolicyBody_SchemaAnnotation() {
        testCompletion(it -> {
            final var policy = "policy \"test\" permit where var foo = 1 s";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("schema");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_emptySchema() {
        testCompletion(it -> {
            final var policy = "policy \"test\" deny where var foo = 1 schema {}; fo";
            it.setModel(policy);
            it.setColumn(policy.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_InvalidSchema() {
        testCompletion(it -> {
            final var policy = "policy \"test\" permit where var foo = \"test\" schema {;!}; fo";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_getSchemaFromJSONinText() {
        testCompletion(it -> {
            final var policy = """
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
                    foo""";

            final var cursor = "foo";
            it.setModel(policy);
            it.setLine(11);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.age", "foo.name", "foo.name.firstname");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_suggestSchemaPathsAfterDot() {
        testCompletion(it -> {
            final var policy = """
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
                    foo.""";

            final var cursor = "foo.";
            it.setModel(policy);
            it.setLine(11);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.age", "foo.name", "foo.name.firstname");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("filter.blacken");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_getNestedSchemaFromEnvironmentVariable() {
        testCompletion(it -> {
            final var policy = "policy \"test\" permit where var bar = 5; var foo = \"test\" schema schema_with_additional_keywords; foo";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.subject.age", "foo.subject.name", "foo.subject.name.firstname");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_getNestedSchemaFromEnvironmentVariable2() {
        testCompletion(it -> {
            final var policy = """
                      policy "test" permit where
                    final var foo = "test" schema schema_with_additional_keywords;
                      foo
                    final var foobar = 1;""";
            final var cursor = "foo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo.subject.age", "foo.subject.name", "foo.subject.name.firstname");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_SchemaNotInEnvironmentVariable() {
        testCompletion(it -> {
            final var policy = "policy \"test\" permit where var foo = \"test\" schema non_existent_schema; fo";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_NotSuggestEnumKeywords() {
        testCompletion(it -> {
            final var policy = """
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
                     foo""";

            final var cursor = "foo";
            it.setModel(policy);
            it.setLine(16);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo", "foo.java", "foo.java.name");
                final var unwanted = List.of("foo.", "foo.name.enum[0]", "foo.name.enum[1]");
                assertProposalsSimple(expected, completionList);
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_SuggestArrayItems() {
        testCompletion(it -> {
            final var policy = """
                    policy "test" permit where var bar = 3; var foo = "test" schema
                    {
                      "type": "array",
                      "items": [
                        { "type": "string" },
                        { "enum": ["Street", "Avenue"] }
                      ]
                    };
                    foo""";

            final var cursor = "foo";
            it.setModel(policy);
            it.setLine(8);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo[]");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestPDPScopedVariable_NotSuggestOutOfScopeVariable() {
        testCompletion(it -> {
            final var policy = "subject schema policy \"test\" permit where var foo = 5;";
            final var cursor = "subject schema ";
            it.setModel(policy);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = environmentVariableNames;
                final var unwanted = List.of("foo");
                assertProposalsSimple(expected, completionList);
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementSubject() {
        testCompletion(it -> {
            final var policy = "subject schema general_schema policy \"test\" permit where subject";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.age", "subject.name", "subject.name.firstname");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementAction() {
        testCompletion(it -> {
            final var policy = "action schema general_schema policy \"test\" permit where action";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("action.name", "action.age");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariableWithNameContainingSubjectAuthzElement() {
        testCompletion(it -> {
            final var policy = "subject schema subject_schema policy \"test\" permit where subject";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariableWithNameContainingActionAuthzElement() {
        testCompletion(it -> {
            final var policy = "action schema action_schema policy \"test\" permit where action";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("action");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElement_with_idsteps() {
        testCompletion(it -> {
            final var policy = "subject schema general_schema policy \"test\" permit where subject.name";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject.name", "subject.name.firstname");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("var", "filter.blacken");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElement_with_idsteps_with_trailing_dot() {
        testCompletion(it -> {
            final var policy = "subject schema general_schema policy \"test\" permit where subject.name.";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject.name", "subject.name.firstname");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("var", "filter.blacken");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElement_with_incomplete_id_step() {
        testCompletion(it -> {
            final var policy = "subject schema general_schema policy \"test\" permit where subject.a";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject.age");
                assertProposalsSimple(expected, completionList);
                final var unwanted = List.of("var", "filter.blacken");
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_getFromJSONinText_for_AuthzElementSubject() {
        testCompletion(it -> {
            final var policy = """
                    subject schema {
                    "properties":
                        {"name": {"type": "object", "properties": {"firstname": {"type": "string"}}},
                         "age": {"type": "number"}}
                        }
                    policy "test" deny where subject
                    """;
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(5);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject.age", "subject.name", "subject.name.firstname");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_recursive_schema() {
        testCompletion(it -> {
            final var policy = """
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
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(11);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.children", "subject.children[]",
                        "subject.children[].children", "subject.children[].children[]",
                        "subject.children[].children[].children", "subject.children[].children[].children[]",
                        "subject.children[].children[].children[].children",
                        "subject.children[].children[].children[].children[]",
                        "subject.children[].children[].children[].children[].children",
                        "subject.children[].children[].children[].children[].children[]",
                        "subject.children[].children[].children[].children[].name",
                        "subject.children[].children[].children[].name", "subject.children[].children[].name",
                        "subject.children[].name", "subject.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_array_with_empty_items() {
        testCompletion(it -> {
            final var policy = """
                    subject schema
                     {
                       "type": "object",
                       "properties": {
                         "name": {
                           "type": "array"
                         }
                       }
                     }
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(9);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_resolveInternalReferences() {
        testCompletion(it -> {
            final var policy = """
                    subject schema
                     {
                        "type": "object",
                        "properties": {
                          "name": { "$ref": "#/$defs/name" }                                    },
                        "$defs": {
                          "name": {"type": "object", "properties": {"first_name": {"type": "string"},
                          "last_name": {"type": "string"}}
                        }
                      }
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(10);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.name", "subject.name.first_name",
                        "subject.name.last_name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferences() {
        testCompletion(it -> {
            final var policy = """
                    subject schema
                     {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "shipping_address": { "$ref": "https://example.com/address.schema.json" }
                         }
                      }
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(8);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.name", "subject.shipping_address",
                        "subject.shipping_address.country-name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferenceCombinedWithInternalReference() {
        testCompletion(it -> {
            final var policy = """
                    subject schema
                     {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "place_of_birth": { "$ref": "https://example.com/calendar.schema.json#/properties/geo" }
                         }
                      }
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(8);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.name", "subject.place_of_birth");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferenceToNonExistingSchema() {
        testCompletion(it -> {
            final var policy = """
                    subject schema
                     {
                        "type": "object",
                        "properties": {
                          "place_of_birth": { "$ref": "notexisting_schema" }
                         }
                      }
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(7);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.place_of_birth");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_resolveExternalReferenceToNonExistingSchemaWithInternalReference() {
        testCompletion(it -> {
            final var policy = """
                    subject schema
                     {
                        "type": "object",
                        "properties": {
                          "place_of_birth": { "$ref": "notexisting_schema/#/geo" }
                         }
                      }
                     policy "test" deny where subject""";
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(7);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject", "subject.place_of_birth");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_missingChildAttribute() {
        testCompletion(it -> {
            final var policy = """
                    policy "test" deny where var foo = 1 schema {
                      "properties": {
                        "name": {
                          }
                        }
                      }
                    };
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(7);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo", "foo.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_patternProperties() {
        testCompletion(it -> {
            final var policy = """
                    policy "test" deny where var foo = 1 schema
                    {
                      "type": "object",
                      "patternProperties": {
                        "^S_": { "type": "string" },
                        "^I_": { "type": "integer" }
                      }
                    };
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(8);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo");
                final var unwanted = List.of("foo.patternProperties", "foo.patternProperties.^S_");
                assertProposalsSimple(expected, completionList);
                assertDoesNotContainProposals(unwanted, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_without_import() {
        testCompletion(it -> {
            final var policy = """
                    policy "test" deny where var foo = schemaTest.person();
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(1);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo", "foo.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_wildcard_import() {
        testCompletion(it -> {
            final var policy = """
                    import schemaTest.*
                    policy "test" deny where var foo = person();
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo", "foo.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_PolicyBody_function_with_alias_import() {
        testCompletion(it -> {
            final var policy = """
                    import schemaTest as test
                    policy "test" deny where var foo = test.person();
                    fo""";

            final var cursor = "fo";
            it.setModel(policy);
            it.setLine(2);
            it.setColumn(cursor.length());

            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("foo", "foo.name");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    void testCompletion_proposal_contains_space() {
        testCompletion(it -> {
            final var policy = """
                    subject schema {
                    "properties":
                        {
                         "first name": {"type": "string"}}
                        }
                    policy "test" deny where subject
                    """;
            final var cursor = "policy \"test\" deny where subject";
            it.setModel(policy);
            it.setLine(5);
            it.setColumn(cursor.length());
            it.setAssertCompletionList(completionList -> {
                final var expected = List.of("subject.'first name'");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

}
