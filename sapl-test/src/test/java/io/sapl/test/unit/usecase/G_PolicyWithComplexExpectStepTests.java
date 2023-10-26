/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue;
import static io.sapl.hamcrest.Matchers.hasObligationMatching;
import static io.sapl.hamcrest.Matchers.hasResourceMatching;
import static io.sapl.hamcrest.Matchers.isPermit;
import static org.hamcrest.CoreMatchers.allOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;
import lombok.Data;

class G_PolicyWithComplexExpectStepTests {

    private static final String BLACKENED_ICD11 = "ic███████";

    private static final String BLACKENED_DIAGNOSIS = "█████████████";

    private static final String WILLI_HAS_ACCESSED_PATIENT_DATA = "Willi has accessed patient data (id=56) as an administrator.";

    private SaplTestFixture fixture;

    private ObjectMapper mapper;

    @Data
    static class SubjectDTO {
        public String name      = "Willi";
        public String authority = "ROLE_ADMIN";
    }

    private final Object subject = new SubjectDTO();

    @Data
    static class ActionDTO {

        @Data
        static class JavaDTO {
            public String name = "findById";
        }

        public JavaDTO java = new JavaDTO();
    }

    private final Object action = new ActionDTO();

    @Data
    static class ResourceDTO {
        public String id            = "56";
        public String diagnosisText = "diagnosisText";
        public String icd11Code     = "icd11Code";
    }

    private final Object resource = new ResourceDTO();

    @BeforeEach
    public void setUp() throws InitializationException {
        fixture = new SaplUnitTestFixture("policyWithObligationAndResource.sapl")
                .registerFunctionLibrary(new FilterFunctionLibrary());
        mapper  = new ObjectMapper();
    }

    @Test
    void test_equals() {
        var obligation = mapper.createObjectNode();
        obligation.put("type", "logAccess");
        obligation.put("message", WILLI_HAS_ACCESSED_PATIENT_DATA);
        var obligations = mapper.createArrayNode();
        obligations.add(obligation);

        var resource = mapper.createObjectNode();
        resource.put("id", "56");
        resource.put("diagnosisText", BLACKENED_DIAGNOSIS);
        resource.put("icd11Code", BLACKENED_ICD11);

        var decision = new AuthorizationDecision(Decision.PERMIT).withObligations(obligations).withResource(resource);

        fixture.constructTestCase().when(AuthorizationSubscription.of(subject, action, resource)).expect(decision)
                .verify();

    }

    @Test
    void test_equalsPredicate() {
        fixture.constructTestCase().when(AuthorizationSubscription.of(subject, action, resource))
                .expect((AuthorizationDecision dec) -> {

                    if (dec.getDecision() != Decision.PERMIT) {
                        return false;
                    }

                    if (dec.getObligations().isEmpty() || dec.getResource().isEmpty()) {
                        return false;
                    }

                    // check obligation
                    var containsExpectedObligation = false;
                    for (var node : dec.getObligations().get()) {
                        if (node.has("type") && "logAccess".equals(node.get("type").asText()) && node.has("message")
                                && WILLI_HAS_ACCESSED_PATIENT_DATA.equals(node.get("message").asText())) {
                            containsExpectedObligation = true;
                        }
                    }

                    // check resource
                    var containsExpectedResource = false;
                    var resource         = dec.getResource().get();
                    if (resource.has("id") && "56".equals(resource.get("id").asText()) && resource.has("diagnosisText")
                            && BLACKENED_DIAGNOSIS.equals(resource.get("diagnosisText").asText())
                            && resource.has("icd11Code")
                            && BLACKENED_ICD11.equals(resource.get("icd11Code").asText())) {
                        containsExpectedResource = true;
                    }

                    return containsExpectedObligation && containsExpectedResource;
                }).verify();

    }

    @Test
    void test_equalsMatcher() {
        fixture.constructTestCase().when(AuthorizationSubscription.of(subject, action, resource))
                .expect(allOf(isPermit(),

                        // check Obligations
                        // via .equals()
                        //// hasObligation(mapper.createObjectNode().put("foo", "bar")),
                        // or Predicate
                        hasObligationMatching((JsonNode obligation) -> {
                            return obligation.has("type") && "logAccess".equals(obligation.get("type").asText())
                                    && obligation.has("message")
                                    && WILLI_HAS_ACCESSED_PATIENT_DATA.equals(obligation.get("message").asText());
                        }),

                        hasObligationContainingKeyValue("type", "logAccess"),

                        // check Advice
                        // via .equals()
                        //// hasAdvice(mapper.createObjectNode().put("foo", "bar")),
                        // or Predicate
                        //// hasAdviceMatching((JsonNode advice) -> {
                        //// return advice.has("sendEmail");
                        //// }),

                        // check Resource
                        // via .equals()
                        //// isResourceEquals(new
                        // ObjectMapper().createObjectNode().put("foo", "bar")),
                        // or Predicate
                        hasResourceMatching((JsonNode resource) -> resource.has("id")
                                && "56".equals(resource.get("id").asText()) && resource.has("diagnosisText")
                                && BLACKENED_DIAGNOSIS.equals(resource.get("diagnosisText").asText())
                                && resource.has("icd11Code")
                                && BLACKENED_ICD11.equals(resource.get("icd11Code").asText()))))
                .verify();

    }

}
