


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

            import java.util.ArrayList;
            import java.util.List;

            /**
             * Tests regarding the autocompletion of schema statements
             */
            @SpringBootTest
            @ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
            public class SchemaCompletionTests extends CompletionTests {

                List<String> environmentVariableNames = List.of("schema_with_additional_keywords",
                        "bank_action_schema", "subject_schema");

                /**
                 * Tests regarding the preamble
                 */

                @Test
                public void testCompletion_Preamble_Empty_ReturnsSchemaKeyword() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("schema");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_Preamble_PartialSchemaKeyword_ReturnsSchemaKeyword() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schem";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("schema");
                            assertEqualProposals(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_Preamble_SchemaNameIsEmptyString_ReturnsEnvironmentVariables() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = environmentVariableNames;
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_Preamble_SchemaStatement_ReturnsKeyword() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema \"text\" ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("for");
                            assertEqualProposals(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_Preamble_SubscriptionElementIsAuthzElement() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema \"test\" for ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject", "action", "resource", "environment");
                            assertEqualProposals(expected, completionList);
                        });
                    });
                }

                /**
                 * Tests regarding the policy body
                 */

                @Test
                public void testCompletion_PolicyBody_SchemaAnnotation() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where s";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("schema");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_PolicyBody_SchemaAnnotationNameIsEmptyString() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where schema ";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = new ArrayList<String>();
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_PolicyBody_getFromJSONinText() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"220530_testpolicy\" deny where schema {" +
                                "\"properties\":" +
                                "  {" +
                                "    \"name\":" +
                                "    {\"type\": \"object\"," +
                                "     \"properties\":" +
                                "     {\"firstname\": {\"type\": \"string\"}}" +
                                "    }," +
                                "    \"age\": {\"type\": \"number\"}" +
                                "  }" +
                                "} var foo = 1; foo";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("foo.age", "foo.name", "foo.name.firstname");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_PolicyBody_getSchemaFromEnvironmentVariable() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var bar = 5; schema schema_with_additional_keywords var foo = \"test\"; foo";
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
                public void testCompletion_PolicyBody_NotSuggestEnumKeywords() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "policy \"test\" permit where var bar = 3; schema bank_action_schema var foo = \"test\"; ";
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
                public void testCompletion_SuggestPDPScopedVariable_NotSuggestOutOfScopeVariable() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema policy \"test\" permit where var foo = 5;";
                        String cursor = "schema ";
                        it.setModel(policy);
                        it.setColumn(cursor.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = environmentVariableNames;
                            var unwanted = List.of("foo", "bar");
                            assertProposalsSimple(expected, completionList);
                            assertDoesNotContainProposals(unwanted, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementSubject() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema subject_schema for subject policy \"test\" permit where subject";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.name", "subject.age");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementAction() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema myschema for action policy \"test\" permit where action";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("action.name", "action.age");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_SuggestSchemaFromPDPScopedVariable_for_AuthzElementAction2() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema subject_schema for action policy \"test\" permit where action";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("action.name", "action.age");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

                @Test
                public void testCompletion_getFromJSONinText_for_AuthzElementSubject() {
                    testCompletion((TestCompletionConfiguration it) -> {
                        String policy = "schema {" +
                                "\"properties\":" +
                                "  {" +
                                "    \"name\":" +
                                "    {\"type\": \"object\"," +
                                "     \"properties\":" +
                                "     {\"firstname\": {\"type\": \"string\"}}" +
                                "    }," +
                                "    \"age\": {\"type\": \"number\"}" +
                                "  }" +
                                "} for subject policy \"testpolicy\" deny where subject";
                        it.setModel(policy);
                        it.setColumn(policy.length());
                        it.setAssertCompletionList(completionList -> {
                            var expected = List.of("subject.age", "subject.name", "subject.name.firstname");
                            assertProposalsSimple(expected, completionList);
                        });
                    });
                }

            }

