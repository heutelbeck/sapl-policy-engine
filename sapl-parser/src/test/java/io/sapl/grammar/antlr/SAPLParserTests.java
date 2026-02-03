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
package io.sapl.grammar.antlr;

import org.antlr.v4.runtime.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SAPLParserTests {

    private static final Path VALID_POLICIES_DIR = Path.of("src/test/resources/policies/valid");

    static Stream<Path> validPolicyFiles() throws IOException {
        return Files.list(VALID_POLICIES_DIR).filter(path -> path.toString().endsWith(".sapl")).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPolicyFiles")
    void whenParsingValidPolicyFile_thenNoSyntaxErrors(Path policyPath) throws IOException {
        var policyContent = Files.readString(policyPath, StandardCharsets.UTF_8);
        var ctxAndErrors  = parseAndCollectErrors(policyContent);
        var errors        = ctxAndErrors.errors();
        assertThat(errors).as("Parsing '%s' should produce no syntax errors", policyPath.getFileName()).isEmpty();
    }

    static Stream<Arguments> policies() {
        return Stream.of(
                // Simple Policies
                arguments("simple write denial", """
                        policy "policy 2"
                        deny

                            action == "write";
                        """), arguments("simple read permit", """
                        policy "policy read"
                        permit

                            action == "read";
                        """), arguments("apple eating permit", """
                        policy "policy1"
                        permit

                            action == "eat";
                            resource == "apple";
                        """), arguments("read with variable comparison", """
                        policy "policy read"
                        permit

                            action == "read";
                            test == 1;
                        """), arguments("read for subject willi", """
                        policy "policySimple"
                        permit

                            action == "read";
                            subject == "willi";
                        """), arguments("deny foo for WILLI", """
                        policy "policy_A"
                        deny

                            resource == "foo";
                            "WILLI" == subject;
                        """), arguments("permit foo for WILLI", """
                        policy "policy_B"
                        permit

                            resource == "foo";
                            "WILLI" == subject;
                        """), arguments("empty permit", """
                        policy "test policy"
                        permit
                        """), arguments("empty deny", """
                        policy "test policy"
                        deny
                        """), arguments("body with regex", """
                        policy "test policy"
                        deny

                            action =~ "some regex";
                        """), arguments("complex boolean body with conjunction", """
                        policy "test policy"
                        permit

                            subject == "aSubject" & target == "aTarget";
                        """), arguments("disjunction in body", """
                        policy "test policy"
                        permit

                            (subject == "aSubject") | (target == "aTarget");
                        """), arguments("negation in body", """
                        policy "test policy"
                        permit

                            !(subject == "aSubject" | target == "aTarget");
                        """), arguments("variable definition in body", """
                        policy "test policy"
                        permit

                            var subject_id = subject.metadata.id;
                            !("a" == "b");
                            action =~ "HTTP.GET";
                        """),

                // Functions
                arguments("day of week function check",
                        """
                                policy "policyWithSimpleFunction"
                                permit

                                    action == "read";
                                    time.dayOfWeek("2021-02-08T16:16:33.616Z") =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),

                // Attributes
                arguments("upper case subject and time PIP",
                        """
                                policy "policy 1"
                                permit

                                    action == "read";
                                    subject.<test.upper> == "WILLI";
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("upper case subject with variable",
                        """
                                policy "policy 1"
                                permit

                                    action == "read";
                                    subject.<test.upper> == "WILLI";
                                    var test = 1;
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("eat icecream with PIPs",
                        """
                                policy "policy eat icecream"
                                permit

                                    action == "eat" & resource == "icecream";
                                    subject.<test.upper> == "WILLI";
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("environment attribute", """
                        policy "policyWithEnvironmentAttribute"
                        permit

                            action == "write";
                            <org.emergencyLevel> == 0;
                        """), arguments("simple upper case PIP", """
                        policy "policyWithSimplePIP"
                        permit

                            action == "read";
                            subject.<test.upper> == "WILLI";
                        """),
                arguments("multiple functions and PIPs",
                        """
                                policy "policyWithMultipleFunctionsOrPIPs"
                                permit

                                    action == "read";
                                    subject.<test.upper> == "WILLI";
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("streaming time attribute", """
                        policy "policyStreaming"
                        permit

                          resource == "heartBeatData";
                          subject == "ROLE_DOCTOR";
                          var interval = 2;
                          time.secondOf(<time.now(interval)>) > 4;
                        """), arguments("streaming time attribute variant", """
                        policy "policyStreaming"
                        permit

                          resource == "bar";
                          subject == "WILLI";
                          var interval = 2;
                          time.secondOf(<time.now(interval)>) >= 4;
                        """), arguments("head attribute finder", """
                        policy "headAttribute"
                        permit

                            |<clock.ticker> != undefined;
                            subject.|<user.updates> == true;
                        """), arguments("attribute finder with options", """
                        policy "attributeWithOptions"
                        permit

                            subject.<user.data[{"option": true, "timeout": 5000}]> == "data";
                        """),

                // Obligations, Advice, Transforms
                arguments("mongo query manipulation with obligation", """
                        policy "permit query method (1)"
                        permit

                            action == "fetchingByQueryMethod";
                            subject.age > 18;
                        obligation {
                                       "type": "mongoQueryManipulation",
                                       "conditions": [ "{'age': {'$gt': 18}}" ]
                                     }
                        """), arguments("mongo with blacklist selection", """
                        policy "permit method name query (1)"
                        permit

                            action == "findAll";
                            subject.age > 18;
                        obligation {
                                       "type": "mongoQueryManipulation",
                                       "conditions": [ "{'admin': {'$eq': false}}" ],
                                       "selection": {
                                            "type": "blacklist",
                                            "columns": ["firstname"]
                                        }
                                     }
                        """), arguments("streaming with obligation A", """
                        policy "policy 1"
                        permit

                             action == "read";
                             subject == "WILLI";
                             time.secondOf(<time.now>) < 20;
                        obligation "A"
                        """),
                arguments("obligation and transform with blacken",
                        """
                                policy "policyWithObligationAndResource"
                                permit

                                    action.java.name == "findById";
                                    "ROLE_ADMIN" in subject..authority;
                                obligation
                                    {
                                        "type" : "logAccess",
                                        "message" : subject.name + " has accessed patient data (id="+resource.id+") as an administrator."
                                    }
                                transform
                                    resource |- {
                                                    @.icd11Code : blacken(2,0,"X"),
                                                    @.diagnosisText : blacken(0,0,"X")
                                                }
                                """),
                arguments("simple transformation", """
                        policy "test"
                        permit
                        transform
                            "teststring"
                        """), arguments("transform with boolean constant", """
                        policy "policy"
                        permit
                        transform
                            true
                        """), arguments("obligation only", """
                        policy "p"
                        permit
                        obligation
                            "wash your hands"
                        """), arguments("advice only", """
                        policy "p"
                        permit
                        advice
                            "smile"
                        """), arguments("obligation advice and transform combined", """
                        policy "p"
                        permit

                            true;
                        obligation
                            "wash your hands"
                        advice
                            "smile"
                        transform
                            [true,false,null]
                        """), arguments("transform with body expression", """
                        policy "p"
                        permit

                            true && true;
                        transform
                            "aaa"
                        """), arguments("multiple obligations", """
                        policy "test"
                        permit
                        obligation
                            { "type": "log", "message": "access granted" }
                        obligation
                            { "type": "notify", "recipient": "admin" }
                        """), arguments("multiple advice", """
                        policy "test"
                        permit
                        advice
                            { "type": "cache", "duration": 300 }
                        advice
                            { "type": "rate_limit", "max": 100 }
                        """),

                // Schemas
                arguments("basic schema enforcement", """
                        subject enforced schema {
                            "$schema": "https://json-schema.org/draft/2020-12/schema",
                            "type": "string"
                        }
                        policy "test"
                        permit
                        """), arguments("schema enforcement with body", """
                        subject enforced schema {
                            "$schema": "https://json-schema.org/draft/2020-12/schema",
                            "type": "string"
                        }
                        policy "test"
                        permit

                            true;
                        """), arguments("multiple schemas", """
                        subject enforced schema {
                            "$schema": "https://json-schema.org/draft/2020-12/schema",
                            "type": "string"
                        }
                        action enforced schema {
                            "$schema": "https://json-schema.org/draft/2020-12/schema",
                            "type": "string"
                        }
                        policy "test"
                        permit
                        """), arguments("non-enforced schema", """
                        subject schema {
                            "$schema": "https://json-schema.org/draft/2020-12/schema",
                            "type": "string"
                        }
                        policy "test"
                        permit
                        """), arguments("all subscription elements with schemas", """
                        subject schema { "type": "object" }
                        action enforced schema { "type": "string" }
                        resource schema { "required": ["id"] }
                        environment schema { "type": "object" }
                        policy "schema test"
                        permit
                        """), arguments("variable with schema", """
                        policy "p" permit

                           var x = 123 schema { "type": "number" };
                        """), arguments("variable with multiple schemas", """
                        policy "p" permit

                           var x = 123 schema { "type": "number" }, { "minimum": 0 };
                        """), arguments("combining algorithm keywords as identifiers", """
                        policy "keywords as variables" permit

                           var priority = 5;
                           var first = true;
                           var unique = "value";
                           var unanimous = [1, 2, 3];
                           var strict = { "key": "value" };
                           var errors = 0;
                           var abstain = false;
                           var propagate = null;
                           priority > 3;
                           first == true;
                        """), arguments("complex expressions",
                        """
                                import filter.blacken
                                import simple.append
                                import simple.length


                                    policy "policy read"
                                    permit
                                    action == "read";
                                    subject == "willi" & resource =~ "some.+";
                                  1 in [0, [{"text": 1, "arr": [3, 4, 5]}, 1, 2 / 2]]..[2];
                                  [0, [{"text": 1, "arr": [3, 4, 5]}, 1, 2], 6]..* == [0, [{"text": 1, "arr": [3, 4, 5]}, 1, 2], {"text": 1, "arr": [3, 4, 5]}, 1, [3, 4, 5], 3, 4, 5, 1, 2, 6];

                                    var a = {"name": "Felix", "origin": "Zurich"};
                                var b = {"name": "Hans", "origin": "Hagen"};
                                        [a, b] |- { each @..name : append(" from ", @.origin), each @..origin : remove } == [{"name": "Felix from Zurich"}, {"name": "Hans from Hagen"}];

                                var input = "SAPL rocks";
                                input.<echo.echo> == "SAPL rocks";

                                obligation
                                {
                                    "type" : "logAccess",
                                        "message" : subject + " has read " + resource
                                }

                                transform
                                {"name": "Homer"} |- { @.name : blacken(2,0,"\\u2588") }
                                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policies")
    void whenParsingPolicy_thenNoSyntaxErrors(String description, String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        assertThat(ctxAndErrors.saplContext).isNotNull();
        var errors = ctxAndErrors.errors();
        assertThat(errors).as("Policy '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> policySets() {
        return Stream.of(arguments("policy set with permit policy", """
                set "tests" priority deny or abstain errors propagate
                policy "testp" permit
                """), arguments("policy set with deny policy", """
                set "tests" priority deny or abstain errors propagate
                policy "testp" deny
                """), arguments("policy set with body condition", """
                set "tests" priority deny or abstain errors propagate
                policy "testp" deny subject == "non-matching";
                """), arguments("policy set with multiple policies", """
                set "tests" priority deny or abstain errors propagate
                policy "testp1" permit
                policy "testp2" deny
                """), arguments("policy set with imports", """
                import filter.replace
                import filter.replace
                set "tests" priority deny or abstain errors propagate
                policy "testp1" permit true;
                """), arguments("policy set with set-level variables", """
                set "tests" priority deny or abstain errors propagate
                var var1 = true;
                policy "testp1" permit var1 == true;
                """), arguments("policy set with policy-level variables", """
                set "tests" priority deny or abstain errors propagate
                var var1 = true;
                policy "testp1" permit var var2 = 10; var2 == 10;
                policy "testp2" deny !(var1 == true);
                """), arguments("priority permit algorithm", """
                set "test" priority permit or abstain errors propagate
                policy "deny policy" deny
                policy "permit policy" permit
                """), arguments("priority permit or deny algorithm", """
                set "test" priority permit or deny
                policy "not applicable" permit subject == "non-matching";
                """), arguments("priority deny or permit algorithm", """
                set "test" priority deny or permit
                policy "not applicable" deny subject == "non-matching";
                """), arguments("unique algorithm", """
                set "test" unique or abstain errors propagate
                policy "permit policy" permit
                policy "deny policy" deny
                """), arguments("first algorithm", """
                set "test" first or abstain errors propagate
                policy "not applicable" permit subject == "non-matching";
                policy "permit policy" permit
                policy "deny policy" deny
                """), arguments("policy set with obligation", """
                set "test" priority deny or abstain errors propagate
                policy "permit with obligation" permit obligation { "type": "log" }
                """), arguments("policy set with advice", """
                set "test" priority deny or abstain errors propagate
                policy "permit with advice" permit advice { "type": "info" }
                """), arguments("policy set with transformation", """
                set "test" priority deny or abstain errors propagate
                policy "permit with transform" permit transform { "modified": true }
                """), arguments("policy set with nested conditions", """
                set "test" priority deny or abstain errors propagate
                var setVar = true;
                policy "complex policy" permit

                  var policyVar = 10;
                  setVar == true;
                  policyVar > 5;
                  policyVar < 20;
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policySets")
    void whenParsingPolicySet_thenNoSyntaxErrors(String description, String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("Policy set '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> jwtPolicies() {
        return Stream.of(
                arguments("JWT untrusted issuer denial",
                        """
                                policy "api_filter_jwt:untrusted_issuer"
                                deny

                                    !(jwt.payload(subject).iss in ["https://www.ftk.de/", "http://192.168.2.115:8080/", "http://localhost:8090"]);
                                """),
                arguments("JWT no authorities denial", """
                        policy "api_filter_jwt:nothing_allow_none"
                        deny

                            jwt.payload(subject).authorities == [];
                        """), arguments("JWT admin allow all", """
                        policy "api_filter_jwt:admin_allow_all"
                        permit

                            "ROLE_ADMIN" in jwt.payload(subject).authorities;
                            "ROLE_ADMIN" in subject.<jwt.payload>.authorities;
                        """), arguments("JWT client blacken print object", """
                        policy "api_filter_jwt:client_blacken_printobject"
                        permit

                            "GET" == action.method;
                            action.path.requestPath =~ "^/api/production-plans/.*/print-objects";
                            "ROLE_CLIENT" in jwt.payload(subject).authorities;
                            "ROLE_CLIENT" in subject.<jwt.payload>.authorities;
                        transform
                            resource |- {@..customerName : blacken}
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jwtPolicies")
    void whenParsingJwtPolicy_thenNoSyntaxErrors(String description, String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("JWT policy '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> xacmlStylePolicies() {
        return Stream.of(arguments("XACML simple policy with email domain", """
                policy "SimplePolicy1"
                /* Any subject with an e-mail name in the med.example.com
                   domain can perform any action on any resource. */
                permit

                    subject =~ "(?i).*@med\\\\.example\\\\.com";
                """), arguments("XACML rule 1 patient read record", """
                policy "rule_1"
                /* A person may read any medical record in the
                    http://www.med.example.com/schemas/record.xsd namespace
                    for which he or she is the designated patient */

                permit

                    resource._type == "urn:example:med:schemas:record";
                    string.starts_with(resource._selector, "@");
                    action == "read";
                    subject.role == "patient";
                    subject.patient_number == resource._content.patient.patient_number;
                """),
                arguments("XACML rule 2 parent guardian with PIP",
                        """
                                policy "rule_2"
                                /* A person may read any medical record in the
                                    http://www.med.example.com/records.xsd namespace
                                    for which he or she is the designated parent or guardian,
                                    and for which the patient is under 16 years of age */

                                permit

                                    resource._type == "urn:example:med:schemas:record";
                                    string.starts_with(resource._selector, "@");
                                    action == "read";
                                    subject.role == "parent_guardian";
                                    subject.parent_guardian_id == resource._content.patient.patient_number.<patient.profile>.parentGuardian.id;
                                    date.diff("years", environment.current_date, resource._content.patient.dob) < 16;
                                """),
                arguments("XACML rule 3 physician write with obligation", """
                        policy "rule_3"
                        /* A physician may write any medical element in a record
                            for which he or she is the designated primary care
                            physician, provided an email is sent to the patient */

                        permit

                            subject.role == "physician";
                            string.starts_with(resource._selector, "@.medical");
                            action == "write";
                            subject.physician_id == resource._content.primaryCarePhysician.registrationID;
                        obligation
                            {
                                "id" : "email",
                                "mailto" : resource._content.patient.contact.email,
                                "text" : "Your medical record has been accessed by:" + subject.id
                            }
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("xacmlStylePolicies")
    void whenParsingXacmlStylePolicy_thenNoSyntaxErrors(String description, String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("XACML-style policy '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> languageFeatureSyntax() {
        return Stream.of(
                // Imports
                arguments("simple import", "import simple.append policy \"test\" permit"),
                arguments("import with alias", "import simple.append as concat policy \"test\" permit"),
                arguments("multiple imports", "import simple.length import simple.append policy \"test\" permit"),
                arguments("nested library imports", """
                        import filter.blacken
                        import filter.replace as rep
                        import some.nested.library.function
                        import very.deeply.nested.module.function as shortName
                        policy "imports" permit
                        """),

                // Operators
                arguments("all operator", """
                        policy "operator" permit

                            true || false; true && false; true | false; true ^ false; true & false;
                            1 == 1; 1 != 2; "test" =~ "t.*";
                            1 < 2; 1 <= 2; 1 > 0; 1 >= 0; 1 in [1, 2, 3];
                            1 + 2; 3 - 1; 2 * 3; 6 / 2; 7 % 3;
                            !false; -1; +1;
                        """),

                // Combining Algorithms
                arguments("priority deny algorithm",
                        "set \"test\" priority deny or abstain errors propagate policy \"p\" permit"),
                arguments("priority permit algorithm",
                        "set \"test\" priority permit or abstain errors propagate policy \"p\" permit"),
                arguments("first algorithm", "set \"test\" first or abstain errors propagate policy \"p\" permit"),
                arguments("unique algorithm", "set \"test\" unique or abstain errors propagate policy \"p\" permit"),
                arguments("priority permit or deny algorithm",
                        "set \"test\" priority permit or deny policy \"p\" permit"),
                arguments("priority deny or permit algorithm",
                        "set \"test\" priority deny or permit policy \"p\" permit"),

                // Steps and Path Expressions
                arguments("all step types", """
                        policy "steps" permit

                            subject.name; subject["name"]; subject.*; subject[0]; subject[-1];
                            subject[0:10]; subject[0:10:2]; subject[:10]; subject[0:];
                            subject[(expression)]; subject[?(@ > 5)];
                            subject[0, 1, 2]; subject["a", "b", "c"];
                            subject..name; subject..*; subject..[0]; subject..["escaped"];
                        """), arguments("recursive descent", """
                        policy "recursive descent" permit

                            "ROLE_ADMIN" in subject..authority;
                            resource..customerName == "test";
                        """),

                // Filters and Subtemplates
                arguments("simple filter", """
                        policy "filters" permit transform resource |- blacken
                        """), arguments("filter with arguments", """
                        policy "filters with args" permit transform resource |- blacken(2, 0, "X")
                        """), arguments("extended filters", """
                        policy "extended filters" permit transform
                            resource |- { @.field1 : blacken, each @.items : remove, @ : uppercase }
                        """), arguments("each filter", """
                        policy "each filter" permit transform resource |- each blacken
                        """), arguments("subtemplate", """
                        policy "subtemplate" permit transform resource :: { "filtered": @ }
                        """),

                // JSON Values
                arguments("json values",
                        """
                                policy "json values" permit

                                    var obj = { "string": "value", "number": 42, "float": 3.14, "bool": true, "null": null, "undefined": undefined };
                                    var arr = [1, 2, 3, "mixed", true, null];
                                    var nested = { "array": [1, 2], "object": { "deep": "value" } };
                                    var scientific = 1.5e10; var negativeExp = 1e-5; var negative = -42;
                                    var emptyObj = {}; var emptyArr = [];
                                """),
                arguments("identifier as object key", """
                        policy "identifier keys" permit var obj = { key: "value", anotherKey: 42 };
                        """),

                // Comments
                arguments("comments", """
                        // Single line comment
                        /* Multi-line comment */
                        policy "comments" // inline comment
                        permit /* block */ action == "test";
                        """),

                // Reserved Identifiers as Field Names
                arguments("reserved identifiers as field names", """
                        policy "reserved identifiers" permit

                            subject.subject; action.action; resource.resource; environment.environment;
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageFeatureSyntax")
    void whenParsingLanguageFeature_thenNoSyntaxErrors(String description, String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("Language feature '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> arraySlicingWithDoubleColon() {
        return Stream.of(
                // Core test: :: without whitespace should parse as slice, not subtemplate
                arguments("slice with negative step [::-1]", """
                        policy "test" permit var x = array[::-1];
                        """), arguments("slice with negative step [::-2]", """
                        policy "test" permit var x = array[::-2];
                        """), arguments("slice with positive step [::1]", """
                        policy "test" permit var x = array[::1];
                        """), arguments("slice with positive step [::3]", """
                        policy "test" permit var x = array[::3];
                        """), arguments("slice with empty step [::]", """
                        policy "test" permit var x = array[::];
                        """),

                // With start index
                arguments("slice with start and negative step [1::-1]", """
                        policy "test" permit var x = array[1::-1];
                        """), arguments("slice with start and positive step [2::2]", """
                        policy "test" permit var x = array[2::2];
                        """),

                // Negative indices combined with :: token
                arguments("slice with negative start and step [-1::-1]", """
                        policy "test" permit var x = array[-1::-1];
                        """), arguments("slice with negative start [-3::]", """
                        policy "test" permit var x = array[-3::];
                        """),

                // Equivalent forms with whitespace (uses COLON COLON instead of SUBTEMPLATE)
                arguments("slice with whitespace [: :-1]", """
                        policy "test" permit var x = array[: :-1];
                        """), arguments("slice with whitespace [: :2]", """
                        policy "test" permit var x = array[: :2];
                        """),

                // Full slice syntax (all three parts)
                arguments("full slice [1:5:2]", """
                        policy "test" permit var x = array[1:5:2];
                        """), arguments("full slice with negatives [-5:-1:1]", """
                        policy "test" permit var x = array[-5:-1:1];
                        """),

                // In transform context (where :: subtemplate would also be valid)
                arguments("slice in transform [::-1]", """
                        policy "test" permit transform resource.items[::-1]
                        """), arguments("slice then subtemplate [::-1] :: @", """
                        policy "test" permit transform resource.items[::-1] :: { "value": @ }
                        """),

                // Multiple slices chained
                arguments("chained slices [::2][::-1]", """
                        policy "test" permit var x = array[::2][::-1];
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("arraySlicingWithDoubleColon")
    void whenParsingArraySlicingWithDoubleColon_thenNoSyntaxErrors(String description, String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("Array slicing '%s' should parse without errors", description).isEmpty();
    }

    static Stream<Arguments> invalidSyntaxSnippets() {
        return Stream.of(
                // From SAPLSyntaxErrorMessageProviderTests
                arguments("empty document", ""), arguments("incomplete set - missing name", "set "),
                arguments("incomplete set - missing entitlement", "set \"setname\" "),
                arguments("set without policy", "set \"setname\" priority permit or deny"),
                arguments("incomplete import", "import "), arguments("incomplete policy - missing name", "policy "),
                arguments("incomplete policy - missing entitlement trimmed", "policy \"test\""),
                arguments("incomplete policy - missing entitlement with whitespace", "policy \"test\" "),
                arguments("incomplete variable - missing name trimmed", "policy \"\" deny var"),
                arguments("incomplete variable - missing name with whitespace", "policy \"\" deny var "),
                arguments("incomplete variable - missing assignment trimmed", "policy \"\" deny var abc"),
                arguments("incomplete variable - missing assignment with whitespace", "policy \"\" deny var abc "),
                arguments("incomplete variable - missing value trimmed", "policy \"\" deny var abc ="),
                arguments("incomplete variable - missing value with whitespace", "policy \"\" deny var abc = "),
                arguments("incomplete variable - missing semicolon trimmed", "policy \"\" deny var abc = 5"),
                arguments("incomplete variable - missing semicolon with whitespace", "policy \"\" deny var abc = 5 "),
                // From SAPLValidatorTests
                arguments("invalid keyword", "defect"),
                // From DefaultSAPLParserTests
                arguments("invalid syntax xyz", "xyz"),
                arguments("misplaced comma in target", "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432"),
                // Additional syntax errors
                arguments("unclosed string", "policy \"test permit"),
                arguments("invalid operator ===", "policy \"test\" permit subject === action"),
                arguments("unclosed parenthesis", "policy \"test\" permit (subject == action"),
                arguments("unclosed brace in object", "policy \"test\" permit { \"key\": \"value\""),
                arguments("unclosed bracket in array", "policy \"test\" permit [1, 2, 3"),
                arguments("invalid combining algorithm", "set \"test\" invalid-algorithm policy \"p\" permit"),
                arguments("double colon without expression", "policy \"test\" permit transform resource ::"),
                arguments("filter without function", "policy \"test\" permit transform resource |-"));
    }

    @ParameterizedTest(name = "reject: {0}")
    @MethodSource("invalidSyntaxSnippets")
    void whenParsingInvalidSyntax_thenSyntaxErrorsAreReported(String description, String snippet) {
        var ctxAndErrors = parseAndCollectErrors(snippet);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("Parsing invalid SAPL (%s) should produce syntax errors", description).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "policy \"p\" permit var subject = {};", "policy \"p\" permit var action = {};",
            "policy \"p\" permit var resource = {};", "policy \"p\" permit var environment = {};" })
    void whenUsingReservedWordAsVariableName_thenSyntaxErrorIsReported(String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors).as("Using reserved word as variable name should produce syntax error").isNotEmpty();
    }

    static Stream<Arguments> syntacticallyValidButSemanticallyInvalidPolicies() {
        return Stream.of(
                // Attribute finders in schema (semantic error, not syntax)
                arguments("attribute finder in schema", "subject schema subject.<pip.test> policy \"test\" permit"),
                arguments("environment attribute in schema", "subject schema <time.now> policy \"test\" permit"),
                // Division by zero in body (runtime error, not syntax)
                arguments("division by zero in body", "policy \"test\" permit 17 / 0;"),
                // JSON object comparison in body (runtime behavior, not syntax)
                arguments("JSON object comparison in body",
                        "policy \"test\" permit { \"key\" : \"value\" } == { \"key\": \"value\" };"));
    }

    @ParameterizedTest(name = "syntactically valid: {0}")
    @MethodSource("syntacticallyValidButSemanticallyInvalidPolicies")
    void whenParsingSyntacticallyValidButSemanticallyInvalidPolicy_thenNoSyntaxErrors(String description,
            String policy) {
        var ctxAndErrors = parseAndCollectErrors(policy);
        var errors       = ctxAndErrors.errors();
        assertThat(errors)
                .as("Policy '%s' is syntactically valid (semantic errors are checked separately)", description)
                .isEmpty();
    }

    record CtxAndErrors(SAPLParser.SaplContext saplContext, List<String> errors, SAPLParser parser) {}

    private CtxAndErrors parseAndCollectErrors(String input) {
        var errors      = new ArrayList<String>();
        var charStream  = CharStreams.fromString(input);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);

        var errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String message, RecognitionException exception) {
                errors.add("line %d:%d %s".formatted(line, charPositionInLine, message));
            }
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        var saplContext = parser.sapl();

        return new CtxAndErrors(saplContext, errors, parser);
    }

}
