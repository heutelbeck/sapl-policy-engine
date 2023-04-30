


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

                List<String> environmentVariableNames = List.of("schema_with_additional_keywords",
                        "bank_action_schema", "subject_schema");

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
                 * Tests regarding the policy body
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
                void testCompletion_PolicyBody_getSchemaFromJSONinText() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" deny where var foo = 1 schema {" +
                                "\"properties\":" +
                                "  {" +
                                "    \"name\":" +
                                "    {\"type\": \"object\"," +
                                "     \"properties\":" +
                                "     {\"firstname\": {\"type\": \"string\"}}" +
                                "    }," +
                                "    \"age\": {\"type\": \"number\"}" +
                                "  }" +
                                "}; foo";
                        it.setModel(policy);
                        it.setColumn(policy.length());
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
                void testCompletion_PolicyBody_NotSuggestEnumKeywords() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var bar = 3; var foo = \"test\" schema bank_action_schema; ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of(
                                    "foo.name.registerNewCustomer",
                                    "foo.name.changeAddress",
                                    "foo.name.offerCheapLoan");
                            var unwanted = List.of(
                                    "foo.", "foo.name.enum[0]", "foo.name.enum[1]", "foo.name.enum[2]");
                            assertProposalsSimple(expected, completionList);
                            assertDoesNotContainProposals(unwanted, completionList);
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
                        String policy = "subject schema subject_schema policy \"test\" permit where subject.";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.name", "subject.age", "subject.name.firstname");
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
                            var expected = List.of("action.process", "action.result", "action.process.duration", "action.type_of_action");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                void testCompletion_getFromJSONinText_for_AuthzElementSubject() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "subject schema {" +
                                "\"properties\":" +
                                "  {" +
                                "    \"name\":" +
                                "    {\"type\": \"object\"," +
                                "     \"properties\":" +
                                "     {\"firstname\": {\"type\": \"string\"}}" +
                                "    }," +
                                "    \"age\": {\"type\": \"number\"}" +
                                "  }" +
                                "} policy \"test\" deny where subject";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.age", "subject.name", "subject.name.firstname");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }


                @Test
                void testCompletion_PolicyBody_getNestedSchemaFromEnvironmentVariable2() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var foo = \"test\" schema schema_with_additional_keywords, bank_action_schema; foo";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo.subject.age", "foo.subject.name",
                                    "foo.subject.name.firstname", "foo.name.registerNewCustomer");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }



            }

