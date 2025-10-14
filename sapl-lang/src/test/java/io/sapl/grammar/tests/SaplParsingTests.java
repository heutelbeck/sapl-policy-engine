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
package io.sapl.grammar.tests;

import com.google.inject.Inject;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.validation.SAPLSyntaxErrorMessageProvider;
import lombok.SneakyThrows;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.xbase.lib.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(InjectionExtension.class)
@InjectWith(SAPLInjectorProvider.class)
class SaplParsingTests {
    @Inject
    @Extension
    private ParseHelper<SAPL> parseHelper;

    @Inject
    @Extension
    private ValidationTestHelper validator;

    // @formatter:off
    private static final String[] VALID_POLICY_TEST_CASES = new String[]  {
        "policySet",
        """
        set "A policy set" deny-overrides
        policy "test policy"
        permit
        where { "a" : ^subject.id, "b" : [ resource, action, environment.id ] };
        """,

        "targetExperiment",
        """
        policy "test policy"
        permit { "a" : subject.id, "b" : [ resource, action, environment.id ] }
        """,

        "targetEmptyPermit",
        """
        policy "test policy"
        permit
        """,

        "targetEmptyDeny",
        """
        policy "test policy"
        deny
        """,

        "targetOneSimpleMatchA",
        """
        policy "test policy"
        permit subject == "some:subject"
        """,

        "targetCompareJSONObjects1",
        """
        policy "test policy"
        permit { "key" : "value" } == { "key": "value", "id" : 1234, "active" : false }
        """,

        "targetCompareJSONObjects2",
        """
        policy "test policy"
        permit subject.index[4].rules == { "key": "value", "id" : 1234, "active" : false }
        """,

        "targetOneSimpleMatchB",
        """
        policy "test policy"
        deny action =~ "some regex"
        """,

        "header01",
        """
        policy "test policy"
        permit test
        """,

        "header02",
        """
        policy "test policy"
        permit false
        """,

        "header03",
        """
        policy "test policy"
        permit { "test" : 0.12 }
        """,

        "headerWithSubjectAttributeMatcher",
        """
        policy "test policy"
        permit subject.id =~ "^(?U)[\\\\p{Alpha}\\\\-'. [^=\\\\[\\\\]$()<>;]]*$"
        """,

        "headerWithComplexSubjectAttributeMatcher",
        """
        policy "test policy"
        permit subject.patterns[7].foo.bar == "something"
        """,

        "headerWithMatcherConjunctionA",
        """
        policy "test policy"
        permit subject == "aSubject" & target == "aTarget"
        """,

        "headerWithMatcherConjunctionB",
        """
        policy "test policy"
        permit (subject == "aSubject" & target == "aTarget")
        """,

        "headerWithMatcherConjunctionC",
        """
        policy "test policy"
        permit ((subject == "aSubject") & (target == "aTarget"))
        """,

        "headerWithMatcherDisjunctionA",
        """
        policy "test policy"
        permit subject == "aSubject" | target == "aTarget"
        """,

        "headerWithMatcherDisjunctionB",
        """
        policy "test policy"
        permit (subject == "aSubject" | target == "aTarget")
        """,

        "headerWithMatcherDisjunctionC",
        """
        policy "test policy"
        permit ((subject == "aSubject") | (target == "aTarget"))
        """,

        "headersWithNegationsA",
        """
        policy "test policy"
        permit !subject == "aSubject" | target == "aTarget"
        """,

        "headersWithNegationsB",
        """
        policy "test policy"
        permit !(subject == "aSubject" | target == "aTarget")
        """,

        "headersWithNegationsC",
        """
        policy "test policy"
        permit ((subject == { "id" : "x27", "name": "willi" }) | !target == "aTarget")
        """,

        "headersComplexNestedExpression",
        """
        policy "test policy"
        permit (
                  (
                     (
                       !subject == "aSubject" | target == "aTarget"
                     )
                     &
                     !environment.data[2].errors =~ "regex"
                  )
                  |
                  false == true
               )
               &
               (
                  action.volume == "some" | action.name == "bar"
               )
        """,

        "rulesAssignment1",
        """
        policy "test policy"
        permit
        where
                var something = { "key" : "value"}.key.<external.attribute> ;
        """,

        "rulesAssignment2",
        """
        policy "test policy"
        permit
        where
                var something = { "key" : "value"}.key.<external.attribute>[7].other_key ;
        """,

        "rulesAssignment3",
        """
        policy "test policy"
        permit
        where
                var something1 = { "key" : "value"}.key.<external.attribute>[7].other_key ;
                var something2 = action.http.method;
                var something3 = subject.id;
                var something3 = resource.path.elements[4].<extern.other>;
                var something3 = !( environment.time.current == "2010-01-01T12:00:00+01:00" );
        """,

        "rulesAssignmentAndExpression",
        """
        policy "test policy"
        permit
        where
          var subject_id = subject.metadata.id;
          !("a" == "b");
          action =~ "HTTP.GET";
        """,

        "rulesExpression",
        """
        policy "test policy"
        permit
        where
          var subject_id = subject.metadata.id;
          !("a" == "b");
          action =~ "HTTP.GET";
        """,

        "rulesExpressionAndImport",
        """
        policy "test policy"
        permit
        where
          var subject_id = subject.metadata.id;
          !("a" == "b");
          action =~ "HTTP.GET";
        """,

        "namedPolicy",
        """
        policy "test policy"
        permit
        where
          var subject_id = subject.metadata.id;
          !("a" == "b");
          action =~ "HTTP.GET";
        """,

        "commentedPolicy",
        """
        policy "test policy"
        /*
           this is a comment
        */
        permit
        where
          var subject_id = subject.metadata.id;
          !("a" == "b");
          action =~ "HTTP.GET";
        """,

        "namedAndCommentedPolicy",
        """
        /*
           this is a comment
        */
        policy "test policy"
        permit
        where
          var subject_id = subject.metadata.id;
          !("a" == "b");
          action =~ "HTTP.GET";
        """,

        "simpleTransformPolicy",
        """
        policy "policy"
            permit
            transform
                true
        """,

        "ourPuppetDoctorTransformPolicy",
        """
        policy "doctors_hide_icd10"
            permit
                subject.role == "doctor" &
                action.verb == "show_patientdata"
            transform
                resource |- {
                    @.address : remove,
                    @.medicalhistory_icd10 : blacken(1,0,"")
                }

        """,

        "ourPuppetFamilyMemberTransformPolicy",
        """
        policy "familymembers_truncate_contexthistory"
            permit
                subject.role == "familymember" &
                action.verb == "show_patient_contexthistory"
            transform
                {
                    "patientid" : resource.patientid,
                    "contexthistory" : resource.contexthistory[0:-1] :: {
                        "datetime" : @.datetime,
                        "captureid" : @.captureid,
                        "status" : @.status
                    }
                }
        """,

        "ourPuppetIntroducerTransformPolicy",
        """
        policy "puppetintroducers_truncate_contexthistory"
            permit
                subject.role == "puppetintroducer" &
                action.verb == "show_patient_contexthistory"
            transform
                {
                    "patientid" : resource.patientid,
                    "detected_situations" : resource.detected_situations[?(isOfToday(@.datetime))] :: {
                        "datetime" : @.datetime,
                        "captureid" : @.captureid,
                        "status" : @.status
                    }
                }
        """
    };
    // @formatter:on

    private static Stream<Arguments> validSaplDocuments() {
        final var arguments = new Arguments[VALID_POLICY_TEST_CASES.length / 2];
        for (var i = 0; i < VALID_POLICY_TEST_CASES.length; i = i + 2) {
            arguments[i / 2] = Arguments.of(VALID_POLICY_TEST_CASES[i], VALID_POLICY_TEST_CASES[i + 1]);
        }
        return Stream.of(arguments);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("validSaplDocuments")
    void validSaplDocumentsParseWithoutError(String testCase, String saplDocument) {
        assertThat(testCase).isNotNull();
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void rulesEmpty() throws Exception {
        final var saplDocument = """
                policy "test policy" permit
                deny subject.id =~ "http://*"
                where
                """;
        validator.assertError(parseHelper.parse(saplDocument), SaplPackage.eINSTANCE.getSAPL(),
                Diagnostic.SYNTAX_DIAGNOSTIC);
    }

    @Test
    void emptyPolicy() throws Exception {
        final var saplDocument = " ";
        validator.assertError(this.parseHelper.parse(saplDocument), SaplPackage.eINSTANCE.getSAPL(),
                Diagnostic.SYNTAX_DIAGNOSTIC, SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT_ERROR);
    }

}
