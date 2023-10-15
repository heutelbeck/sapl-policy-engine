


            /*
             * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

            import org.eclipse.xtext.testing.TestCompletionConfiguration;
            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.test.context.ContextConfiguration;

            import java.util.List;

            /**
             * Tests regarding the autocompletion of schema statements
             */
            @SpringBootTest
            @ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
            class SchemaCompletionTests extends CompletionTests {

                List<String> environmentVariableNames = List.of("calendar_schema");

                /**
                 * Tests regarding the preamble
                 */

                @Test
                void testCompletion_Preamble_ReturnsSchemaKeyword_For_Subject() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("schema");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_Preamble_PartialSchemaKeyword_ReturnsSchemaKeyword() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject schem";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("schema");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_Preamble_SchemaNameIsEmptyString_ReturnsEnvironmentVariables() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject schema ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = environmentVariableNames;
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_Preamble_SubscriptionElementIsAuthzElement() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = " schema \"test\"";
                        it.setModel(policy);
                        it.setColumn(0);
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject", "action", "resource", "environment");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                /**
                 * Tests regarding variable annotation in policy body
                 */

                @Test
                void testCompletion_PolicyBody_SchemaAnnotation() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var foo = 1 s";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("schema");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_emptySchema() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" deny where var foo = 1 schema {}; foo";
                        it.setModel(policy);
                        it.setColumn(policy.length());

                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_getSchemaFromJSONinText() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
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

                        String cursor = "foo";
                        it.setModel(policy);
                        it.setLine(11);
                        it.setColumn(cursor.length());

                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo.age", "foo.name", "foo.name.firstname");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_getNestedSchemaFromEnvironmentVariable() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var bar = 5; var foo = \"test\" schema schema_with_additional_keywords; foo";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo.subject.age", "foo.subject.name",
                                    "foo.subject.name.firstname");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_SchemaNotInEnvironmentVariable() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var foo = \"test\" schema non_existent_schema; foo";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_NotSuggestEnumKeywords() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
                                policy "test" permit where var bar = 3; var foo = "test" schema
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

                        String cursor = "foo";
                        it.setModel(policy);
                        it.setLine(16);
                        it.setColumn(cursor.length());

                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of(
                                    "foo.name.registerNewCustomer",
                                    "foo.name.changeAddress");
                            var unwanted = List.of(
                                    "foo.", "foo.name.enum[0]", "foo.name.enum[1]");
                            assertProposalsSimple(expected, completionList);
                            assertDoesNotContainProposals(unwanted, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_SuggestArrayItems() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
                                policy "test" permit where var bar = 3; var foo = "test" schema
                                {
                                  "type": "array",
                                  "items": [
                                    { "type": "string" },
                                    { "enum": ["Street", "Avenue"] }
                                  ]
                                };
                                foo""";

                        String cursor = "foo";
                        it.setModel(policy);
                        it.setLine(8);
                        it.setColumn(cursor.length());

                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo", "foo.Avenue", "foo.Street");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }


                @Test
                void testCompletion_SuggestPDPScopedVariable_NotSuggestOutOfScopeVariable() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject schema policy \"test\" permit where var foo = 5;";
                        String cursor = "subject schema ";
                        it.setModel(policy);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = environmentVariableNames;
                            var unwanted = List.of("foo");
                            assertProposalsSimple(expected, completionList);
                            assertDoesNotContainProposals(unwanted, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementSubject() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject schema general_schema policy \"test\" permit where subject";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.name", "subject.age");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementAction() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "action schema general_schema policy \"test\" permit where action";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("action.name", "action.age");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_SuggestSchemaFromPDPScopedVariableWithNameContainingSubjectAuthzElement() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject schema subject_schema policy \"test\" permit where subject";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_SuggestSchemaFromPDPScopedVariableWithNameContainingActionAuthzElement() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "action schema action_schema policy \"test\" permit where action";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("action");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_getFromJSONinText_for_AuthzElementSubject() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
                                subject schema {
                                "properties":
                                    {"name": {"type": "object", "properties": {"firstname": {"type": "string"}}},
                                     "age": {"type": "number"}}
                                    }
                                policy "test" deny where subject
                                """;
                        String cursor = "policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setLine(5);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.age", "subject.name", "subject.name.firstname");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_recursive_schema() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
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
                        String cursor = "policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setLine(11);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.name", "subject.children",
                                    "subject.children.name", "subject.children.children");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_array_with_empty_items() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
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
                        String cursor = "policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setLine(9);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject", "subject.name");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }


                @Test
                void testCompletion_PolicyBody_resolveInternalReferences() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
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
                        String cursor = "policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setLine(10);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject", "subject.name", "subject.name.first_name",
                                    "subject.name.last_name");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_resolveExternalReferences() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
                                subject schema
                                 {
                                    "type": "object",
                                    "properties": {
                                      "name": { "type": "string" },
                                      "shipping_address": { "$ref": "address_schema" }
                                     }
                                  }
                                 policy "test" deny where subject""";
                        String cursor = "policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setLine(8);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject", "subject.name", "subject.shipping_address", "subject.shipping_address.country-name");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_resolveExternalReferenceCombinedWithInternalReference() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
                                subject schema
                                 {
                                    "type": "object",
                                    "properties": {
                                      "name": { "type": "string" },
                                      "place_of_birth": { "$ref": "calendar_schema/#/properties/geo" }
                                     }
                                  }
                                 policy "test" deny where subject""";
                        String cursor = "policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setLine(8);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject", "subject.name",
                                    "subject.place_of_birth", "subject.place_of_birth.latitude.minimum");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_PolicyBody_missingChildAttribute() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = """
                                policy "test" deny where var foo = 1 schema {
                                  "properties": {
                                    "name": {                                     
                                      }
                                    }
                                  }
                                };
                                foo""";

                        String cursor = "foo";
                        it.setModel(policy);
                        it.setLine(7);
                        it.setColumn(cursor.length());

                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo", "foo.name");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

            }

