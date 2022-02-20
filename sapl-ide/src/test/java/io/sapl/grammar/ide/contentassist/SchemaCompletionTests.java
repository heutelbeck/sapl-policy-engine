


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
 * Tests regarding the auto completion of schema statements
 */
@SpringBootTest
@ContextConfiguration(classes = SAPLIdeSpringTestConfiguration.class)
public class SchemaCompletionTests extends CompletionTests {

    @Test
    public void testCompletion_AtTheBeginningPartialSchemaStatement_ReturnsSchemaKeyword() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "schem";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                //var expected = new ArrayList<String>();
                var expected = List.of("schema");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    public void testCompletion_SchemaNameIsEmptyString() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "schema ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = new ArrayList<String>();
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    public void testCompletion_AtTheBeginningSchemaStatement_ReturnsKeyword() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "schema \"text\" ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("for");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    public void testCompletion_SubscriptionElementIsAuthzElement() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "schema \"test\" for ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = List.of("subject", "action", "resource", "environment");
                assertProposalsSimple(expected, completionList);
            });
        });
    }

    @Test
    public void testCompletion_SchemaAnnotation() {
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
    public void testCompletion_SchemaAnnotationNameIsEmptyString() {
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
    public void testCompletion_blabla() {
        testCompletion((TestCompletionConfiguration it) -> {
            String policy = "schema \"test\" for \"testitem\" policy ";
            it.setModel(policy);
            it.setColumn(policy.length());
            it.setAssertCompletionList(completionList -> {
                var expected = new ArrayList<String>();
                assertProposalsSimple(expected, completionList);
            });
        });
    }

}
