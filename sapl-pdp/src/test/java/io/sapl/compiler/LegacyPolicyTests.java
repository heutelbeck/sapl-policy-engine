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
package io.sapl.compiler;

import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.libraries.*;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.SAPLParser;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive test suite for all legacy SAPL policies found across modules.
 * Tests that existing policies compile
 * successfully with the new compiler.
 */
class LegacyPolicyTests {
    private static final SAPLParser PARSER = new DefaultSAPLParser();
    private CompilationContext      context;

    @BeforeEach
    void setUp() {
        val functionBroker      = new DefaultFunctionBroker();
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);

        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(ArrayFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(BitwiseFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SaplFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SchemaValidationLibrary.class);
        functionBroker.loadStaticFunctionLibrary(MockXACMLStringFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(MockXACMLDateFunctionLibrary.class);

        context = new CompilationContext(functionBroker, attributeBroker);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPolicies")
    void whenValidPolicy_thenCompilesSuccessfully(String description, String policy) {
        val sapl     = PARSER.parse(policy);
        val compiled = SaplCompiler.compileDocument(sapl, context);
        assertThat(compiled).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPolicies")
    void whenInvalidPolicy_thenThrowsCompilerException(String description, String policy) {
        val sapl = PARSER.parse(policy);
        assertThatThrownBy(() -> SaplCompiler.compileDocument(sapl, context)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void whenInvalidSyntax_thenParserThrows() {
        val policy = """
                policy "invalid"
                bla bla bla
                """;
        assertThatThrownBy(() -> PARSER.parse(policy)).hasMessageContaining("Parsing error");
    }

    static Stream<Arguments> validPolicies() {
        return Stream.of(
                // CATEGORY 1: SIMPLE POLICIES (Basic Logic Only)
                arguments("simple write denial", """
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
                        """),

                // CATEGORY 2: POLICIES WITH FUNCTIONS
                arguments("day of week function check",
                        """
                                policy "policyWithSimpleFunction"
                                permit
                                    action == "read"
                                where
                                    time.dayOfWeek("2021-02-08T16:16:33.616Z") =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),

                // CATEGORY 3: POLICIES WITH PIPS/ATTRIBUTES
                arguments("upper case subject and time PIP",
                        """
                                policy "policy 1"
                                permit
                                    action == "read"
                                where
                                    subject.<test.upper> == "WILLI";
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("upper case subject with variable",
                        """
                                policy "policy 1"
                                permit
                                    action == "read"
                                where
                                    subject.<test.upper> == "WILLI";
                                    var test = 1;
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("eat icecream with PIPs",
                        """
                                policy "policy eat icecream"
                                permit
                                    action == "eat" & resource == "icecream"
                                where
                                    subject.<test.upper> == "WILLI";
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("environment attribute", """
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
                        """),
                arguments("multiple functions and PIPs",
                        """
                                policy "policyWithMultipleFunctionsOrPIPs"
                                permit
                                    action == "read"
                                where
                                    subject.<test.upper> == "WILLI";
                                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                                """),
                arguments("streaming time attribute", """
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
                        """),

                // CATEGORY 4: POLICIES WITH COMPLEX FEATURES
                arguments("mongo query manipulation with obligation", """
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
                        """), arguments("mongo with whitelist selection", """
                        policy "permit method name query (2)"
                        permit
                        where
                            action == "findAllByAgeAfter";
                            resource.age >= 18;
                        obligation {
                                       "type": "mongoQueryManipulation",
                                       "conditions": [ "{'admin': {'$eq': false}}" ],
                                       "selection": {
                                            "type": "whitelist",
                                            "columns": ["firstname"]
                                        }
                                     }
                        """), arguments("r2dbc query with whitelist", """
                        policy "permit query method (1)"
                        permit
                        where
                            action == "fetchingByQueryMethod";
                            subject.age > 18;
                        obligation {
                                       "type": "r2dbcQueryManipulation",
                                       "conditions": [ "active = true" ],
                                       "selection": {
                                                "type": "whitelist",
                                                "columns": ["firstname"]
                                        }
                                     }
                        """), arguments("r2dbc with blacklist and transformations", """
                        policy "permit method name query (1)"
                        permit
                        where
                            action == "findAll";
                            subject.age >= 18;
                        obligation {
                                       "type": "r2dbcQueryManipulation",
                                       "conditions": [ "active = false" ],
                                       "selection": {
                                                "type": "blacklist",
                                                "columns": ["age"]
                                                },
                                        "transformations": {
                                            "firstname": "UPPER"
                                            }
                                     }
                        """), arguments("r2dbc with condition only", """
                        policy "permit method name query (2)"
                        permit
                        where
                            action == "findAllByAgeAfter";
                            resource.age >= 18;
                        obligation {
                                       "type": "r2dbcQueryManipulation",
                                       "conditions": [ "active = true" ]
                                     }
                        """), arguments("streaming with obligation A", """
                        policy "policy 1"
                        permit
                             action == "read"
                        where
                             subject == "WILLI";
                             time.secondOf(<time.now>) < 20; obligation "A"
                        """), arguments("streaming with obligation B", """
                        policy "policy 2"
                        permit
                             action == "read"
                        where
                             subject == "WILLI";
                             time.secondOf(<time.now>) < 40; obligation "B"
                        """), arguments("streaming with obligation C", """
                        policy "policy 3"
                        permit
                             action == "read"
                        where
                             subject == "WILLI";
                             time.secondOf(<time.now>) < 60; obligation "C"
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
                                                    @.icd11Code : blacken(2,0,"█"),
                                                    @.diagnosisText : blacken(0,0,"█")
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
                        """), arguments("basic schema enforcement", """
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
                        """),

                // CATEGORY 5: JWT API FILTER POLICIES
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
                        """), arguments("JWT client deny customer", """
                        policy "api_filter_jwt:client_deny_customer"
                        deny
                            action.path.requestPath =~ "^/api/customers.*"
                          & "ROLE_CLIENT" in jwt.payload(subject).authorities
                        """), arguments("JWT non-client allow customer", """
                        policy "api_filter_jwt:nonclient_allow_customer"
                        permit
                            action.path.requestPath =~ "^/api/customers.*"
                          & !("ROLE_CLIENT" in jwt.payload(subject).authorities)
                        where
                            !("ROLE_CLIENT" in subject.<jwt.payload>.authorities);
                        """), arguments("JWT client deny order", """
                        policy "api_filter_jwt:client_deny_order"
                        deny
                            action.path.requestPath =~ "^/api/orders.*"
                          & "ROLE_CLIENT" in jwt.payload(subject).authorities
                        """), arguments("JWT non-client allow order", """
                        policy "api_filter_jwt:nonclient_allow_order"
                        permit
                            action.path.requestPath =~ "^/api/orders.*"
                          & !("ROLE_CLIENT" in jwt.payload(subject).authorities)
                        where
                            !("ROLE_CLIENT" in subject.<jwt.payload>.authorities);
                        """), arguments("JWT engineer/operator allow printjob", """
                        policy "api_filter_jwt:engineer_operator_allow_all_printjob"
                        permit
                            action.path.requestPath =~ "^/api/printjobs.*"
                          & (  "ROLE_ENGINEER" in jwt.payload(subject).authorities
                             | "ROLE_OPERATOR" in jwt.payload(subject).authorities)
                        where
                            "ROLE_ENGINEER" in subject.<jwt.payload>.authorities
                         || "ROLE_OPERATOR" in subject.<jwt.payload>.authorities;
                        """), arguments("JWT authenticated GET printjob", """
                        policy "api_filter_jwt:authenticated_allow_get_printjob"
                        permit
                            "GET" == action.method & action.path.requestPath =~ "^/api/printjobs.*"
                        where
                            "VALID" == subject.<jwt.validity>;
                        """), arguments("JWT client deny 3mf file", """
                        policy "api_filter_jwt:client_deny_original_or_annotated_3mf"
                        deny
                            "GET" == action.method
                          & (  action.path.requestPath =~ "^/api/production-plans/.*/threemf-file"
                             | action.path.requestPath =~ "^/api/production-plans/.*/annotated-threemf-file")
                          & "ROLE_CLIENT" in jwt.payload(subject).authorities
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
                        """), arguments("JWT authenticated GET production plan", """
                        policy "api_filter_jwt:authenticated_allow_get_productionplan"
                        permit
                            "GET" == action.method & action.path.requestPath =~ "^/api/production-plans.*"
                        where
                            "VALID" == subject.<jwt.validity>;
                        """), arguments("JWT client deny file", """
                        policy "api_filter_jwt:client_deny_file"
                        deny
                            action.path.requestPath =~ "^/api/files.*"
                          & "ROLE_CLIENT" in jwt.payload(subject).authorities
                        """), arguments("JWT engineer allow file", """
                        policy "api_filter_jwt:engineer_allow_all_file"
                        permit
                            action.path.requestPath =~ "^/api/files.*"
                          & "ROLE_ENGINEER" in jwt.payload(subject).authorities
                        where
                            "ROLE_ENGINEER" in subject.<jwt.payload>.authorities;
                        """), arguments("JWT authenticated GET file", """
                        policy "api_filter_jwt:authenticated_allow_get_file"
                        permit
                            "GET" == action.method & action.path.requestPath =~ "^/api/files.*"
                        where
                            "VALID" == subject.<jwt.validity>;
                        """), arguments("JWT engineer/operator allow machine", """
                        policy "api_filter_jwt:engineer_operator_allow_all_machine"
                        permit
                            action.path.requestPath =~ "^/api/machines.*"
                          & (  "ROLE_ENGINEER" in jwt.payload(subject).authorities
                             | "ROLE_OPERATOR" in jwt.payload(subject).authorities)
                        where
                            "ROLE_ENGINEER" in subject.<jwt.payload>.authorities
                         || "ROLE_OPERATOR" in subject.<jwt.payload>.authorities;
                        """), arguments("JWT authenticated GET machines", """
                        policy "api_filter_jwt:authenticated_allow_get_machines"
                        permit
                            "GET" == action.method & action.path.requestPath =~ "^/api/machines.*"
                        where
                            "VALID" == subject.<jwt.validity>;
                        """), arguments("JWT client deny subscription anomaly", """
                        policy "api_filter_jwt:client_deny_subscription_anomaly"
                        deny
                            action.path.requestPath =~ "^/api/subscriptions.*/anomaly"
                          & "ROLE_CLIENT" in jwt.payload(subject).authorities
                        """), arguments("JWT authenticated GET subscription", """
                        policy "api_filter_jwt:authenticated_allow_get_subscription"
                        permit
                            "GET" == action.method & action.path.requestPath =~ "^/api/subscriptions.*"
                        where
                            "VALID" == subject.<jwt.validity>;
                        """),

                // CATEGORY 6: XACML-STYLE POLICIES
                arguments("XACML simple policy with email domain", """
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

    static Stream<Arguments> invalidPolicies() {
        return Stream.of(arguments("target with JSON objects always false", """
                policy "test policy"
                permit { "key" : "value" } == { "key": "value", "id" : 1234, "active" : false }
                """), arguments("division by zero in target", """
                policy "policy division by zero"
                permit
                    17 / 0
                """), arguments("division by zero in comparison", """
                policy "policy read"
                permit
                    1/0 == false
                """));
    }
}
