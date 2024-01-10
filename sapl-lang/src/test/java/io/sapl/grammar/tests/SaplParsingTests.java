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
package io.sapl.grammar.tests;

import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.xbase.lib.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.inject.Inject;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.validation.SAPLSyntaxErrorMessageProvider;

@ExtendWith(InjectionExtension.class)
@InjectWith(SAPLInjectorProvider.class)
public class SaplParsingTests {
    @Inject
    @Extension
    private ParseHelper<SAPL> parseHelper;

    @Inject
    @Extension
    private ValidationTestHelper validator;

    @Test
    void policySet() throws Exception {
        var saplDocument = """
                set "A policy set" deny-overrides
                policy "test policy"
                permit
                where { "a" : ^subject.id, "b" : [ resource, action, environment.id ] };
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetExperiment() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit { "a" : subject.id, "b" : [ resource, action, environment.id ] }
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetEmptyPermit() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetEmptyDeny() throws Exception {
        var saplDocument = """
                policy "test policy"
                deny
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetOneSimpleMatchA() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit subject == "some:subject"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetCompareJSONObjects1() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit { "key" : "value" } == { "key": "value", "id" : 1234, "active" : false }
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetCompareJSONObjects2() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit subject.index[4].rules == { "key": "value", "id" : 1234, "active" : false }
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void targetOneSimpleMatchB() throws Exception {
        var saplDocument = """
                policy "test policy"
                deny action =~ "some regex"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    // the null pointer exception is caused by a bug in the testing framework
    // occurring when you have
    // the abstract root class and use expectError.
    // See also: https://www.eclipse.org/forums/index.php/t/1071631/ expecting it is
    // a workaround
    @Test
    void emptyPolicy() throws Exception {
        var saplDocument = " ";
        validator.assertError(this.parseHelper.parse(saplDocument), SaplPackage.eINSTANCE.getSAPL(),
                Diagnostic.SYNTAX_DIAGNOSTIC, SAPLSyntaxErrorMessageProvider.INCOMPLETE_DOCUMENT_ERROR);
    }

    @Test
    void header01() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit test
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void header02() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit false
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void header03() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit { "test" : 0.12 }
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithSubjectAttributeMatcher() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit subject.id =~ "^(?U)[\\\\p{Alpha}\\\\-'. [^=\\\\[\\\\]$()<>;]]*$"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithComplexSubjectAttributeMatcher() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit subject.patterns[7].foo.bar == "something"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithMatcherConjuctionA() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit subject == "aSubject" & target == "aTarget"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithMatcherConjuctionB() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit (subject == "aSubject" & target == "aTarget")
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithMatcherConjuctionC() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit ((subject == "aSubject") & (target == "aTarget"))
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithMatcherDisjuctionA() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit subject == "aSubject" | target == "aTarget"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithMatcherDisjuctionB() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit (subject == "aSubject" | target == "aTarget")
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headerWithMatcherDisjuctionC() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit ((subject == "aSubject") | (target == "aTarget"))
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headersWithNegationsA() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit !subject == "aSubject" | target == "aTarget"
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headersWithNegationsB() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit !(subject == "aSubject" | target == "aTarget")
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headersWithNegationsC() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit ((subject == { "id" : "x27", "name": "willi" }) | !target == "aTarget")
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void headersComplexNestedExpression() throws Exception {
        var saplDocument = """
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
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    // the null pointer exception is caused by a bug in the testing framework
    // occurring when you have
    // the abstract root class and use expectError.
    // See also: https://www.eclipse.org/forums/index.php/t/1071631/ expecting it is
    // a workaround
    @Test
    void rulesEmpty() throws Exception {
        var saplDocument = """
                policy "test policy" permit
                deny subject.id =~ "http://*"
                where
                """;
        validator.assertError(parseHelper.parse(saplDocument), SaplPackage.eINSTANCE.getSAPL(),
                Diagnostic.SYNTAX_DIAGNOSTIC);
    }

    @Test
    void rulesAssignment1() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var something = { "key" : "value"}.key.<external.attribute> ;
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void rulesAssignment2() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var something = { "key" : "value"}.key.<external.attribute>[7].other_key ;
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void rulesAssignment3() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var something1 = { "key" : "value"}.key.<external.attribute>[7].other_key ;
                  var something2 = action.http.method;
                  var something3 = subject.id;
                  var something3 = ressource.path.elements[4].<extern.other>;
                  var something3 = !( environment.time.current == "2010-01-01T12:00:00+01:00" );
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void rulesAssignmentAndExpression() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var subject_id = subject.metadata.id;
                  !("a" == "b");
                  action =~ "HTTP.GET";
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void rulesExpression() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var subject_id = subject.metadata.id;
                  !("a" == "b");
                  action =~ "HTTP.GET";
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void rulesExpressionAndImport() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var subject_id = subject.metadata.id;
                  !("a" == "b");
                  action =~ "HTTP.GET";
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void namedPolicy() throws Exception {
        var saplDocument = """
                policy "test policy"
                permit
                where
                  var subject_id = subject.metadata.id;
                  !("a" == "b");
                  action =~ "HTTP.GET";
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void commentedPolicy() throws Exception {
        var saplDocument = """
                policy "test policy"
                /*
                   this is a comment
                */
                permit
                where
                  var subject_id = subject.metadata.id;
                  !("a" == "b");
                  action =~ "HTTP.GET";
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void namedAndCommentedPolicy() throws Exception {
        var saplDocument = """
                /*
                   this is a comment
                */
                policy "test policy"
                permit
                where
                  var subject_id = subject.metadata.id;
                  !("a" == "b");
                  action =~ "HTTP.GET";
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void simpleTransformPolicy() throws Exception {
        var saplDocument = """
                policy "policy"
                    permit
                    transform
                        true
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void ourPuppetDoctorTransformPolicy() throws Exception {
        var saplDocument = """
                policy "doctors_hide_icd10"
                    permit
                        subject.role == "doctor" &
                        action.verb == "show_patientdata"
                    transform
                        resource |- {
                            @.address : remove,
                            @.medicalhistory_icd10 : blacken(1,0,"")
                        }

                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void ourPuppetFamilymemberTransformPolicy() throws Exception {
        var saplDocument = """
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
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }

    @Test
    void ourPuppetIntroducerTransformPolicy() throws Exception {
        var saplDocument = """
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
                """;
        validator.assertNoErrors(parseHelper.parse(saplDocument));
    }
}
