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
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test suite for all legacy SAPL policies found across modules.
 * Tests that existing policies compile successfully with the new compiler.
 *
 * Policies are organized by complexity:
 * - Simple policies (basic logic only)
 * - Policies with functions
 * - Policies with PIPs/attributes
 * - Policies with complex features (obligations, advice, transformations)
 * - Error/invalid policies (negative tests)
 */
class LegacySaplPoliciesTests {
    private static final SAPLInterpreter PARSER = new DefaultSAPLInterpreter();
    private CompilationContext           context;

    @BeforeEach
    void setUp() throws InitializationException {
        val functionBroker      = new DefaultFunctionBroker();
        val attributeRepository = new InMemoryAttributeRepository(Clock.systemUTC());
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);

        functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(ArrayFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(FilterFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(BitwiseFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SaplFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(SchemaValidationLibrary.class);
        // Mock libraries for XACML-style tests
        functionBroker.loadStaticFunctionLibrary(MockXACMLStringFunctionLibrary.class);
        functionBroker.loadStaticFunctionLibrary(MockXACMLDateFunctionLibrary.class);

        context = new CompilationContext(functionBroker, attributeBroker);
    }

    private void assertPolicyCompiles(String policySource) {
        val sapl     = PARSER.parse(policySource);
        val compiled = SaplCompiler.compileDocument(sapl, context);
        assertThat(compiled).isNotNull();
    }

    // =========================================================================
    // CATEGORY 1: SIMPLE POLICIES (Basic Logic Only)
    // =========================================================================

    @Test
    void simplePolicy_writeDenial() {
        val policy = """
                policy "policy 2"
                deny action == "write"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_readPermit() {
        val policy = """
                policy "policy read"
                permit
                    action == "read"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_appleEating() {
        val policy = """
                policy "policy1"
                permit
                    action == "eat"
                where
                    resource == "apple";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_readWithVariable() {
        val policy = """
                policy "policy read"
                permit
                    action == "read"
                where
                    test == 1;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_readForWilli() {
        val policy = """
                policy "policySimple"
                permit
                    action == "read"
                where
                    subject == "willi";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_denyFooForWilli() {
        val policy = """
                policy "policy_A"
                deny
                    resource == "foo"
                where
                    "WILLI" == subject;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_permitFooForWilli() {
        val policy = """
                policy "policy_B"
                permit
                    resource == "foo"
                where
                    "WILLI" == subject;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_emptyPermit() {
        val policy = """
                policy "test policy"
                permit
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_emptyDeny() {
        val policy = """
                policy "test policy"
                deny
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_targetWithJSONObject() {
        val policy = """
                policy "test policy"
                permit { "key" : "value" } == { "key": "value", "id" : 1234, "active" : false }
                """;
        // This policy has a target that always evaluates to false (different JSON
        // objects)
        assertThatThrownBy(() -> assertPolicyCompiles(policy)).isInstanceOf(SaplCompilerException.class)
                .hasMessageContaining("always returns false");
    }

    @Test
    void simplePolicy_targetWithRegex() {
        val policy = """
                policy "test policy"
                deny action =~ "some regex"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_complexBooleanTarget() {
        val policy = """
                policy "test policy"
                permit (subject == "aSubject" & target == "aTarget")
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_disjunctionTarget() {
        val policy = """
                policy "test policy"
                permit ((subject == "aSubject") | (target == "aTarget"))
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_negationInTarget() {
        val policy = """
                policy "test policy"
                permit !(subject == "aSubject" | target == "aTarget")
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void simplePolicy_variableDefinitionInBody() {
        val policy = """
                policy "test policy"
                permit
                where
                    var subject_id = subject.metadata.id;
                    !("a" == "b");
                    action =~ "HTTP.GET";
                """;
        assertPolicyCompiles(policy);
    }

    // =========================================================================
    // CATEGORY 2: POLICIES WITH FUNCTIONS (No PIPs/Attributes)
    // =========================================================================

    @Test
    void functionPolicy_dayOfWeekCheck() {
        val policy = """
                policy "policyWithSimpleFunction"
                permit
                    action == "read"
                where
                    time.dayOfWeek("2021-02-08T16:16:33.616Z") =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """;
        assertPolicyCompiles(policy);
    }

    // =========================================================================
    // CATEGORY 3: POLICIES WITH PIPS/ATTRIBUTES
    // =========================================================================

    @Test
    void pipPolicy_upperCaseSubjectAndTime() {
        val policy = """
                policy "policy 1"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_upperCaseSubjectWithVariable() {
        val policy = """
                policy "policy 1"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                    var test = 1;
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_eatIceCream() {
        val policy = """
                policy "policy eat icecream"
                permit
                    action == "eat" & resource == "icecream"
                where
                    subject.<test.upper> == "WILLI";
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_environmentAttribute() {
        val policy = """
                policy "policyWithEnvironmentAttribute"
                permit
                    action == "write"
                where
                    <org.emergencyLevel> == 0;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_simpleUpperCase() {
        val policy = """
                policy "policyWithSimplePIP"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_multipleFunctionsAndPIPs() {
        val policy = """
                policy "policyWithMultipleFunctionsOrPIPs"
                permit
                    action == "read"
                where
                    subject.<test.upper> == "WILLI";
                    time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_streamingTimeAttribute() {
        val policy = """
                policy "policyStreaming"
                permit
                  resource == "heartBeatData"
                where
                  subject == "ROLE_DOCTOR";
                  var interval = 2;
                  time.secondOf(<time.now(interval)>) > 4;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void pipPolicy_streamingTimeAttributeVariant() {
        val policy = """
                policy "policyStreaming"
                permit
                  resource == "bar"
                where
                  subject == "WILLI";
                  var interval = 2;
                  time.secondOf(<time.now(interval)>) >= 4;
                """;
        assertPolicyCompiles(policy);
    }

    // =========================================================================
    // CATEGORY 4: POLICIES WITH COMPLEX FEATURES
    // =========================================================================

    @Test
    void complexPolicy_mongoQueryManipulationWithObligation() {
        val policy = """
                policy "permit query method (1)"
                permit
                where
                    action == "fetchingByQueryMethod";
                    subject.age > 18;
                obligation {
                               "type": "mongoQueryManipulation",
                               "conditions": [ "{'age': {'$gt': 18}}" ]
                             }
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_mongoWithBlacklistSelection() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_mongoWithWhitelistSelection() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_r2dbcQueryWithWhitelist() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_r2dbcWithBlacklistAndTransformations() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_r2dbcWithConditionOnly() {
        val policy = """
                policy "permit method name query (2)"
                permit
                where
                    action == "findAllByAgeAfter";
                    resource.age >= 18;
                obligation {
                               "type": "r2dbcQueryManipulation",
                               "conditions": [ "active = true" ]
                             }
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_streamingWithObligation_A() {
        val policy = """
                policy "policy 1"
                permit
                     action == "read"
                where
                     subject == "WILLI";
                     time.secondOf(<time.now>) < 20; obligation "A"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_streamingWithObligation_B() {
        val policy = """
                policy "policy 2"
                permit
                     action == "read"
                where
                     subject == "WILLI";
                     time.secondOf(<time.now>) < 40; obligation "B"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_streamingWithObligation_C() {
        val policy = """
                policy "policy 3"
                permit
                     action == "read"
                where
                     subject == "WILLI";
                     time.secondOf(<time.now>) < 60; obligation "C"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_withObligationAndTransform() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_simpleTransformation() {
        val policy = """
                policy "test"
                permit
                transform
                    "teststring"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_transformWithConstant() {
        val policy = """
                policy "policy"
                permit
                transform
                    true
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_obligationOnly() {
        val policy = """
                policy "p"
                permit
                obligation
                    "wash your hands"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_adviceOnly() {
        val policy = """
                policy "p"
                permit
                advice
                    "smile"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_obligationAdviceTransform() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_transformWithBody() {
        val policy = """
                policy "p"
                permit
                where
                    (1/10);
                transform
                    "aaa"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_multipleObligations() {
        val policy = """
                policy "test"
                permit
                obligation
                    { "type": "log", "message": "access granted" }
                obligation
                    { "type": "notify", "recipient": "admin" }
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_multipleAdvice() {
        val policy = """
                policy "test"
                permit
                advice
                    { "type": "cache", "duration": 300 }
                advice
                    { "type": "rate_limit", "max": 100 }
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_schemaEnforcementBasic() {
        val policy = """
                subject enforced schema {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "string"
                }
                policy "test"
                permit
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_schemaEnforcementWithTarget() {
        val policy = """
                subject enforced schema {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "string"
                }
                policy "test"
                permit true
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_multipleSchemas() {
        val policy = """
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
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void complexPolicy_nonEnforcedSchema() {
        val policy = """
                subject schema {
                    "$schema": "https://json-schema.org/draft/2020-12/schema",
                    "type": "string"
                }
                policy "test"
                permit
                """;
        assertPolicyCompiles(policy);
    }

    // =========================================================================
    // CATEGORY 5: JWT API FILTER POLICIES
    // =========================================================================

    @Test
    void jwtPolicy_untrustedIssuer() {
        val policy = """
                policy "api_filter_jwt:untrusted_issuer"
                deny
                    !(jwt.payload(subject).iss in ["https://www.ftk.de/", "http://192.168.2.115:8080/", "http://localhost:8090"])
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_noAuthorities() {
        val policy = """
                policy "api_filter_jwt:nothing_allow_none"
                deny
                    jwt.payload(subject).authorities == []
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_adminAllowAll() {
        val policy = """
                policy "api_filter_jwt:admin_allow_all"
                permit
                    "ROLE_ADMIN" in jwt.payload(subject).authorities
                where
                    "ROLE_ADMIN" in subject.<jwt.payload>.authorities;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_clientDenyCustomer() {
        val policy = """
                policy "api_filter_jwt:client_deny_customer"
                deny
                    action.path.requestPath =~ "^/api/customers.*"
                  & "ROLE_CLIENT" in jwt.payload(subject).authorities
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_nonClientAllowCustomer() {
        val policy = """
                policy "api_filter_jwt:nonclient_allow_customer"
                permit
                    action.path.requestPath =~ "^/api/customers.*"
                  & !("ROLE_CLIENT" in jwt.payload(subject).authorities)
                where
                    !("ROLE_CLIENT" in subject.<jwt.payload>.authorities);
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_clientDenyOrder() {
        val policy = """
                policy "api_filter_jwt:client_deny_order"
                deny
                    action.path.requestPath =~ "^/api/orders.*"
                  & "ROLE_CLIENT" in jwt.payload(subject).authorities
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_nonClientAllowOrder() {
        val policy = """
                policy "api_filter_jwt:nonclient_allow_order"
                permit
                    action.path.requestPath =~ "^/api/orders.*"
                  & !("ROLE_CLIENT" in jwt.payload(subject).authorities)
                where
                    !("ROLE_CLIENT" in subject.<jwt.payload>.authorities);
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_engineerOperatorAllowPrintJob() {
        val policy = """
                policy "api_filter_jwt:engineer_operator_allow_all_printjob"
                permit
                    action.path.requestPath =~ "^/api/printjobs.*"
                  & (  "ROLE_ENGINEER" in jwt.payload(subject).authorities
                     | "ROLE_OPERATOR" in jwt.payload(subject).authorities)
                where
                    "ROLE_ENGINEER" in subject.<jwt.payload>.authorities
                 || "ROLE_OPERATOR" in subject.<jwt.payload>.authorities;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_authenticatedGetPrintJob() {
        val policy = """
                policy "api_filter_jwt:authenticated_allow_get_printjob"
                permit
                    "GET" == action.method & action.path.requestPath =~ "^/api/printjobs.*"
                where
                    "VALID" == subject.<jwt.validity>;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_clientDeny3mfFile() {
        val policy = """
                policy "api_filter_jwt:client_deny_original_or_annotated_3mf"
                deny
                    "GET" == action.method
                  & (  action.path.requestPath =~ "^/api/production-plans/.*/threemf-file"
                     | action.path.requestPath =~ "^/api/production-plans/.*/annotated-threemf-file")
                  & "ROLE_CLIENT" in jwt.payload(subject).authorities
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_clientBlackenPrintObject() {
        val policy = """
                policy "api_filter_jwt:client_blacken_printobject"
                permit
                    "GET" == action.method
                  & action.path.requestPath =~ "^/api/production-plans/.*/print-objects"
                  & "ROLE_CLIENT" in jwt.payload(subject).authorities
                where
                    "ROLE_CLIENT" in subject.<jwt.payload>.authorities;
                transform
                    resource |- {@..customerName : blacken}
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_authenticatedGetProductionPlan() {
        val policy = """
                policy "api_filter_jwt:authenticated_allow_get_productionplan"
                permit
                    "GET" == action.method & action.path.requestPath =~ "^/api/production-plans.*"
                where
                    "VALID" == subject.<jwt.validity>;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_clientDenyFile() {
        val policy = """
                policy "api_filter_jwt:client_deny_file"
                deny
                    action.path.requestPath =~ "^/api/files.*"
                  & "ROLE_CLIENT" in jwt.payload(subject).authorities
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_engineerAllowFile() {
        val policy = """
                policy "api_filter_jwt:engineer_allow_all_file"
                permit
                    action.path.requestPath =~ "^/api/files.*"
                  & "ROLE_ENGINEER" in jwt.payload(subject).authorities
                where
                    "ROLE_ENGINEER" in subject.<jwt.payload>.authorities;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_authenticatedGetFile() {
        val policy = """
                policy "api_filter_jwt:authenticated_allow_get_file"
                permit
                    "GET" == action.method & action.path.requestPath =~ "^/api/files.*"
                where
                    "VALID" == subject.<jwt.validity>;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_engineerOperatorAllowMachine() {
        val policy = """
                policy "api_filter_jwt:engineer_operator_allow_all_machine"
                permit
                    action.path.requestPath =~ "^/api/machines.*"
                  & (  "ROLE_ENGINEER" in jwt.payload(subject).authorities
                     | "ROLE_OPERATOR" in jwt.payload(subject).authorities)
                where
                    "ROLE_ENGINEER" in subject.<jwt.payload>.authorities
                 || "ROLE_OPERATOR" in subject.<jwt.payload>.authorities;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_authenticatedGetMachines() {
        val policy = """
                policy "api_filter_jwt:authenticated_allow_get_machines"
                permit
                    "GET" == action.method & action.path.requestPath =~ "^/api/machines.*"
                where
                    "VALID" == subject.<jwt.validity>;
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_clientDenySubscriptionAnomaly() {
        val policy = """
                policy "api_filter_jwt:client_deny_subscription_anomaly"
                deny
                    action.path.requestPath =~ "^/api/subscriptions.*/anomaly"
                  & "ROLE_CLIENT" in jwt.payload(subject).authorities
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void jwtPolicy_authenticatedGetSubscription() {
        val policy = """
                policy "api_filter_jwt:authenticated_allow_get_subscription"
                permit
                    "GET" == action.method & action.path.requestPath =~ "^/api/subscriptions.*"
                where
                    "VALID" == subject.<jwt.validity>;
                """;
        assertPolicyCompiles(policy);
    }

    // =========================================================================
    // CATEGORY 6: ERROR/INVALID POLICIES (Negative Tests)
    // =========================================================================

    @Test
    void errorPolicy_divisionByZero() {
        val policy = """
                policy "policy division by zero"
                permit
                    17 / 0
                """;
        assertThatThrownBy(() -> assertPolicyCompiles(policy)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void errorPolicy_divisionByZeroInComparison() {
        val policy = """
                policy "policy read"
                permit
                    1/0 == false
                """;
        assertThatThrownBy(() -> assertPolicyCompiles(policy)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void errorPolicy_invalidSyntax() {
        val policy = """
                policy "invalid"
                bla bla bla
                """;
        assertThatThrownBy(() -> PARSER.parse(policy)).hasMessageContaining("Parsing error");
    }

    // =========================================================================
    // CATEGORY 7: XACML-STYLE POLICIES (From SampleXACMLTests)
    // =========================================================================

    @Test
    void xacmlPolicy_simplePolicy1() {
        val policy = """
                policy "SimplePolicy1"
                /* Any subject with an e-mail name in the med.example.com
                   domain can perform any action on any resource. */
                permit subject =~ "(?i).*@med\\\\.example\\\\.com"
                """;
        assertPolicyCompiles(policy);
    }

    @Test
    void xacmlPolicy_rule1() {
        val policy = """
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
                """;
        // Original policy from SampleXACMLTests - compiles successfully with mock
        // string library
        assertPolicyCompiles(policy);
    }

    @Test
    void xacmlPolicy_rule2_withPIP() {
        val policy = """
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
                """;
        // Original policy from SampleXACMLTests - compiles with mock libraries
        // (PIPs in where clause conditions are compiled as part of the policy body)
        assertPolicyCompiles(policy);
    }

    @Test
    void xacmlPolicy_rule3_withObligation() {
        val policy = """
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
                """;
        // Original policy from SampleXACMLTests - compiles successfully with mock
        // string library
        assertPolicyCompiles(policy);
    }
}
