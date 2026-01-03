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

/**
 * Comprehensive test suite for the ANTLR4 SAPL parser.
 * Tests derived from sapl-lang and sapl-pdp test suites to ensure
 * 100% syntax compatibility with the Xtext-based parser.
 */
class SAPLParserTests {

    private static final Path VALID_POLICIES_DIR = Path.of("src/test/resources/policies/valid");

    // ========================================================================
    // CATEGORY 1: File-based Policy Tests
    // ========================================================================

    static Stream<Path> validPolicyFiles() throws IOException {
        return Files.list(VALID_POLICIES_DIR).filter(path -> path.toString().endsWith(".sapl")).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPolicyFiles")
    void whenParsingValidPolicyFile_thenNoSyntaxErrors(Path policyPath) throws IOException {
        var policyContent = Files.readString(policyPath, StandardCharsets.UTF_8);
        var errors        = parseAndCollectErrors(policyContent);

        assertThat(errors).as("Parsing '%s' should produce no syntax errors", policyPath.getFileName()).isEmpty();
    }

    // ========================================================================
    // CATEGORY 2: Simple Policy Syntax Tests (from LegacyPolicyTests)
    // ========================================================================

    static Stream<Arguments> simplePolicies() {
        return Stream.of(arguments("simple write denial", """
                policy "policy 2"
                deny action == "write"
                """), arguments("simple read permit", """
                policy "policy read"
                permit
                    action == "read"
                """), arguments("apple eating permit", """
                policy "policy1"
                permit
                    action == "eat"
                where
                    resource == "apple";
                """), arguments("read with variable comparison", """
                policy "policy read"
                permit
                    action == "read"
                where
                    test == 1;
                """), arguments("read for subject willi", """
                policy "policySimple"
                permit
                    action == "read"
                where
                    subject == "willi";
                """), arguments("deny foo for WILLI", """
                policy "policy_A"
                deny
                    resource == "foo"
                where
                    "WILLI" == subject;
                """), arguments("permit foo for WILLI", """
                policy "policy_B"
                permit
                    resource == "foo"
                where
                    "WILLI" == subject;
                """), arguments("empty permit", """
                policy "test policy"
                permit
                """), arguments("empty deny", """
                policy "test policy"
                deny
                """), arguments("target with regex", """
                policy "test policy"
                deny action =~ "some regex"
                """), arguments("complex boolean target with conjunction", """
                policy "test policy"
                permit (subject == "aSubject" & target == "aTarget")
                """), arguments("disjunction target", """
                policy "test policy"
                permit ((subject == "aSubject") | (target == "aTarget"))
                """), arguments("negation in target", """
                policy "test policy"
                permit !(subject == "aSubject" | target == "aTarget")
                """), arguments("variable definition in body", """
                policy "test policy"
                permit
                where
                    var subject_id = subject.metadata.id;
                    !("a" == "b");
                    action =~ "HTTP.GET";
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("simplePolicies")
    void whenParsingSimplePolicy_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 3: Policies with Functions (from LegacyPolicyTests)
    // ========================================================================

    static Stream<Arguments> policiesWithFunctions() {
        return Stream.of(arguments("day of week function check",
                """
                        policy "policyWithSimpleFunction"
                        permit
                            action == "read"
                        where
                            time.dayOfWeek("2021-02-08T16:16:33.616Z") =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesWithFunctions")
    void whenParsingPolicyWithFunctions_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 4: Policies with PIPs/Attributes (from LegacyPolicyTests)
    // ========================================================================

    static Stream<Arguments> policiesWithAttributes() {
        return Stream.of(arguments("upper case subject and time PIP", """
                policy "policy 1"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """), arguments("upper case subject with variable", """
                policy "policy 1"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                    var test = 1;
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """), arguments("eat icecream with PIPs", """
                policy "policy eat icecream"
                permit
                    action == "eat" & resource == "icecream"
                where
                    subject.<test.upper> == "WILLI";
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """), arguments("environment attribute", """
                policy "policyWithEnvironmentAttribute"
                permit
                    action == "write"
                where
                    <org.emergencyLevel> == 0;
                """), arguments("simple upper case PIP", """
                policy "policyWithSimplePIP"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                """), arguments("multiple functions and PIPs", """
                policy "policyWithMultipleFunctionsOrPIPs"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """), arguments("streaming time attribute", """
                policy "policyStreaming"
                permit
                  resource == "heartBeatData"
                where
                  subject == "ROLE_DOCTOR";
                  var interval = 2;
                  time.secondOf(<time.now(interval)>) > 4;
                """), arguments("streaming time attribute variant", """
                policy "policyStreaming"
                permit
                  resource == "bar"
                where
                  subject == "WILLI";
                  var interval = 2;
                  time.secondOf(<time.now(interval)>) >= 4;
                """), arguments("head attribute finder", """
                policy "headAttribute"
                permit
                where
                    |<clock.ticker> != undefined;
                    subject.|<user.updates> == true;
                """), arguments("attribute finder with options", """
                policy "attributeWithOptions"
                permit
                where
                    subject.<user.data[{"option": true, "timeout": 5000}]> == "data";
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesWithAttributes")
    void whenParsingPolicyWithAttributes_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 5: Policies with Obligations, Advice and Transforms
    // ========================================================================

    static Stream<Arguments> policiesWithObligationsAdviceTransform() {
        return Stream.of(arguments("mongo query manipulation with obligation", """
                policy "permit query method (1)"
                permit
                where
                    action == "fetchingByQueryMethod";
                    subject.age > 18;
                obligation {
                               "type": "mongoQueryManipulation",
                               "conditions": [ "{'age': {'$gt': 18}}" ]
                             }
                """), arguments("mongo with blacklist selection", """
                policy "permit method name query (1)"
                permit
                where
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
                     action == "read"
                where
                     subject == "WILLI";
                     time.secondOf(<time.now>) < 20; obligation "A"
                """),
                arguments("obligation and transform with blacken",
                        """
                                policy "policyWithObligationAndResource"
                                permit
                                    action.java.name == "findById"
                                where
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
                        where
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
                        where
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
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesWithObligationsAdviceTransform")
    void whenParsingPolicyWithObligationsAdviceTransform_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 6: Schema Syntax Tests
    // ========================================================================

    static Stream<Arguments> policiesWithSchemas() {
        return Stream.of(arguments("basic schema enforcement", """
                subject enforced schema {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "string"
                }
                policy "test"
                permit
                """), arguments("schema enforcement with target", """
                subject enforced schema {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "string"
                }
                policy "test"
                permit true
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
                where
                   var x = 123 schema { "type": "number" };
                """), arguments("variable with multiple schemas", """
                policy "p" permit
                where
                   var x = 123 schema { "type": "number" }, { "minimum": 0 };
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesWithSchemas")
    void whenParsingPolicyWithSchemas_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 7: Policy Set Syntax Tests (from LegacyPolicySetTests)
    // ========================================================================

    static Stream<Arguments> policySets() {
        return Stream.of(arguments("policy set with permit policy", """
                set "tests" deny-overrides
                policy "testp" permit
                """), arguments("policy set with deny policy", """
                set "tests" deny-overrides
                policy "testp" deny
                """), arguments("policy set with target mismatch", """
                set "tests" deny-overrides
                policy "testp" deny subject == "non-matching"
                """), arguments("policy set with for clause", """
                set "tests" deny-overrides
                for true
                policy "testp" deny subject == "non-matching"
                """), arguments("policy set with multiple policies", """
                set "tests" deny-overrides
                policy "testp1" permit
                policy "testp2" deny
                """), arguments("policy set with imports", """
                import filter.replace
                import filter.replace
                set "tests" deny-overrides
                policy "testp1" permit where true;
                """), arguments("policy set with set-level variables", """
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit var1 == true
                """), arguments("policy set with policy-level variables", """
                set "tests" deny-overrides
                var var1 = true;
                policy "testp1" permit where var var2 = 10; var2 == 10;
                policy "testp2" deny where !(var1 == true);
                """), arguments("permit-overrides algorithm", """
                set "test" permit-overrides
                policy "deny policy" deny
                policy "permit policy" permit
                """), arguments("deny-unless-permit algorithm", """
                set "test" deny-unless-permit
                policy "not applicable" permit subject == "non-matching"
                """), arguments("permit-unless-deny algorithm", """
                set "test" permit-unless-deny
                policy "not applicable" deny subject == "non-matching"
                """), arguments("only-one-applicable algorithm", """
                set "test" only-one-applicable
                policy "permit policy" permit
                policy "deny policy" deny
                """), arguments("first-applicable algorithm", """
                set "test" first-applicable
                policy "not applicable" permit subject == "non-matching"
                policy "permit policy" permit
                policy "deny policy" deny
                """), arguments("policy set with obligation", """
                set "test" deny-overrides
                policy "permit with obligation" permit obligation { "type": "log" }
                """), arguments("policy set with advice", """
                set "test" deny-overrides
                policy "permit with advice" permit advice { "type": "info" }
                """), arguments("policy set with transformation", """
                set "test" deny-overrides
                policy "permit with transform" permit transform { "modified": true }
                """), arguments("policy set with nested conditions", """
                set "test" deny-overrides
                var setVar = true;
                policy "complex policy" permit
                where
                  var policyVar = 10;
                  setVar == true;
                  policyVar > 5;
                  policyVar < 20;
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policySets")
    void whenParsingPolicySet_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Policy set '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 8: JWT API Filter Policies (from LegacyPolicyTests)
    // ========================================================================

    static Stream<Arguments> jwtPolicies() {
        return Stream.of(
                arguments("JWT untrusted issuer denial",
                        """
                                policy "api_filter_jwt:untrusted_issuer"
                                deny
                                    !(jwt.payload(subject).iss in ["https://www.ftk.de/", "http://192.168.2.115:8080/", "http://localhost:8090"])
                                """),
                arguments("JWT no authorities denial", """
                        policy "api_filter_jwt:nothing_allow_none"
                        deny
                            jwt.payload(subject).authorities == []
                        """), arguments("JWT admin allow all", """
                        policy "api_filter_jwt:admin_allow_all"
                        permit
                            "ROLE_ADMIN" in jwt.payload(subject).authorities
                        where
                            "ROLE_ADMIN" in subject.<jwt.payload>.authorities;
                        """), arguments("JWT client blacken print object", """
                        policy "api_filter_jwt:client_blacken_printobject"
                        permit
                            "GET" == action.method
                          & action.path.requestPath =~ "^/api/production-plans/.*/print-objects"
                          & "ROLE_CLIENT" in jwt.payload(subject).authorities
                        where
                            "ROLE_CLIENT" in subject.<jwt.payload>.authorities;
                        transform
                            resource |- {@..customerName : blacken}
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jwtPolicies")
    void whenParsingJwtPolicy_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("JWT policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 9: XACML-Style Policies (from LegacyPolicyTests)
    // ========================================================================

    static Stream<Arguments> xacmlStylePolicies() {
        return Stream.of(arguments("XACML simple policy with email domain", """
                policy "SimplePolicy1"
                /* Any subject with an e-mail name in the med.example.com
                   domain can perform any action on any resource. */
                permit subject =~ "(?i).*@med\\\\.example\\\\.com"
                """), arguments("XACML rule 1 patient read record", """
                policy "rule_1"
                /* A person may read any medical record in the
                    http://www.med.example.com/schemas/record.xsd namespace
                    for which he or she is the designated patient */

                permit
                    resource._type == "urn:example:med:schemas:record" &
                    string.starts_with(resource._selector, "@") &
                    action == "read"
                where
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
                                    resource._type == "urn:example:med:schemas:record" &
                                    string.starts_with(resource._selector, "@") &
                                    action == "read"
                                where
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
                            subject.role == "physician" &
                            string.starts_with(resource._selector, "@.medical") &
                            action == "write"
                        where
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
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("XACML-style policy '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 10: Language Feature Syntax Tests
    // Consolidated tests for imports, operator, steps, filters, JSON, comments
    // ========================================================================

    static Stream<Arguments> languageFeatureSyntax() {
        return Stream.of(
                // --- Imports ---
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

                // --- Operators ---
                arguments("all operator", """
                        policy "operator" permit
                        where
                            true || false; true && false; true | false; true ^ false; true & false;
                            1 == 1; 1 != 2; "test" =~ "t.*";
                            1 < 2; 1 <= 2; 1 > 0; 1 >= 0; 1 in [1, 2, 3];
                            1 + 2; 3 - 1; 2 * 3; 6 / 2; 7 % 3;
                            !false; -1; +1;
                        """),

                // --- Combining Algorithms ---
                arguments("deny-overrides algorithm", "set \"test\" deny-overrides policy \"p\" permit"),
                arguments("permit-overrides algorithm", "set \"test\" permit-overrides policy \"p\" permit"),
                arguments("first-applicable algorithm", "set \"test\" first-applicable policy \"p\" permit"),
                arguments("only-one-applicable algorithm", "set \"test\" only-one-applicable policy \"p\" permit"),
                arguments("deny-unless-permit algorithm", "set \"test\" deny-unless-permit policy \"p\" permit"),
                arguments("permit-unless-deny algorithm", "set \"test\" permit-unless-deny policy \"p\" permit"),

                // --- Steps and Path Expressions ---
                arguments("all step types", """
                        policy "steps" permit
                        where
                            subject.name; subject["name"]; subject.*; subject[0]; subject[-1];
                            subject[0:10]; subject[0:10:2]; subject[:10]; subject[0:];
                            subject[(expression)]; subject[?(@ > 5)];
                            subject[0, 1, 2]; subject["a", "b", "c"];
                            subject..name; subject..*; subject..[0]; subject..["escaped"];
                        """), arguments("recursive descent", """
                        policy "recursive descent" permit
                        where
                            "ROLE_ADMIN" in subject..authority;
                            resource..customerName == "test";
                        """),

                // --- Filters and Subtemplates ---
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

                // --- JSON Values ---
                arguments("json values",
                        """
                                policy "json values" permit
                                where
                                    var obj = { "string": "value", "number": 42, "float": 3.14, "bool": true, "null": null, "undefined": undefined };
                                    var arr = [1, 2, 3, "mixed", true, null];
                                    var nested = { "array": [1, 2], "object": { "deep": "value" } };
                                    var scientific = 1.5e10; var negativeExp = 1e-5; var negative = -42;
                                    var emptyObj = {}; var emptyArr = [];
                                """),
                arguments("identifier as object key", """
                        policy "identifier keys" permit where var obj = { key: "value", anotherKey: 42 };
                        """),

                // --- Comments ---
                arguments("comments", """
                        // Single line comment
                        /* Multi-line comment */
                        policy "comments" // inline comment
                        permit /* block */ action == "test"
                        """),

                // --- Reserved Identifiers as Field Names ---
                arguments("reserved identifiers as field names", """
                        policy "reserved identifiers" permit
                        where
                            subject.subject; action.action; resource.resource; environment.environment;
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("languageFeatureSyntax")
    void whenParsingLanguageFeature_thenNoSyntaxErrors(String description, String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Language feature '%s' should parse without errors", description).isEmpty();
    }

    // ========================================================================
    // CATEGORY 11: Invalid Syntax Tests (should produce errors)
    // ========================================================================

    static Stream<Arguments> invalidSyntaxSnippets() {
        return Stream.of(
                // From SAPLSyntaxErrorMessageProviderTests
                arguments("empty document", ""), arguments("incomplete set - missing name", "set "),
                arguments("incomplete set - missing entitlement", "set \"setname\" "),
                arguments("set without policy", "set \"setname\" deny-unless-permit"),
                arguments("incomplete import", "import "), arguments("incomplete policy - missing name", "policy "),
                arguments("incomplete policy - missing entitlement trimmed", "policy \"test\""),
                arguments("incomplete policy - missing entitlement with whitespace", "policy \"test\" "),
                arguments("incomplete variable - missing name trimmed", "policy \"\" deny where var"),
                arguments("incomplete variable - missing name with whitespace", "policy \"\" deny where var "),
                arguments("incomplete variable - missing assignment trimmed", "policy \"\" deny where var abc"),
                arguments("incomplete variable - missing assignment with whitespace",
                        "policy \"\" deny where var abc "),
                arguments("incomplete variable - missing value trimmed", "policy \"\" deny where var abc ="),
                arguments("incomplete variable - missing value with whitespace", "policy \"\" deny where var abc = "),
                arguments("incomplete variable - missing semicolon trimmed", "policy \"\" deny where var abc = 5"),
                arguments("incomplete variable - missing semicolon with whitespace",
                        "policy \"\" deny where var abc = 5 "),
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
        var errors = parseAndCollectErrors(snippet);
        assertThat(errors).as("Parsing invalid SAPL (%s) should produce syntax errors", description).isNotEmpty();
    }

    // ========================================================================
    // CATEGORY 12: Reserved Words as Variable Names (from DefaultSAPLParserTests)
    // ========================================================================

    @ParameterizedTest
    @ValueSource(strings = { "policy \"p\" permit where var subject = {};",
            "policy \"p\" permit where var action = {};", "policy \"p\" permit where var resource = {};",
            "policy \"p\" permit where var environment = {};" })
    void whenUsingReservedWordAsVariableName_thenSyntaxErrorIsReported(String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors).as("Using reserved word as variable name should produce syntax error").isNotEmpty();
    }

    // ========================================================================
    // CATEGORY 13: Syntactically Valid but Semantically Invalid Policies
    // These should parse without syntax errors (semantic validation is separate)
    // ========================================================================

    static Stream<Arguments> syntacticallyValidButSemanticallyInvalidPolicies() {
        return Stream.of(
                // Lazy operator in target (semantic error, not syntax)
                arguments("lazy AND in target", "policy \"test\" permit a == b && c == d"),
                arguments("lazy OR in target", "policy \"test\" permit a == b || c == d"),
                // Attribute finders in target (semantic error, not syntax)
                arguments("attribute finder in target", "policy \"test\" permit subject.<pip.test> == \"test\""),
                arguments("environment attribute in target", "policy \"test\" permit <time.now> != undefined"),
                // Division by zero in target (runtime error, not syntax)
                arguments("division by zero in target", "policy \"test\" permit 17 / 0"),
                // JSON object comparison in target (runtime behavior, not syntax)
                arguments("JSON object comparison in target",
                        "policy \"test\" permit { \"key\" : \"value\" } == { \"key\": \"value\" }"));
    }

    @ParameterizedTest(name = "syntactically valid: {0}")
    @MethodSource("syntacticallyValidButSemanticallyInvalidPolicies")
    void whenParsingSyntacticallyValidButSemanticallyInvalidPolicy_thenNoSyntaxErrors(String description,
            String policy) {
        var errors = parseAndCollectErrors(policy);
        assertThat(errors)
                .as("Policy '%s' is syntactically valid (semantic errors are checked separately)", description)
                .isEmpty();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private List<String> parseAndCollectErrors(String input) {
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

        parser.sapl();

        return errors;
    }

}
