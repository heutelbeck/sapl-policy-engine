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
package io.sapl.integrationtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecificScenarioTests {

    private static final ObjectMapper MAPPER                = new ObjectMapper();
    private static final String       BOOK3                 = """
            "resource": {
              "id": 3,
              "name": "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",
              "ageRating": 9,
              "content": "Some fantasy story."
            },
            """;
    private static final String       BOOK4                 = """
            "resource":{
              "id":4,
              "name":"The Three-Body Problem",
              "ageRating":14,
              "content":"Space is scary."
            },
            """;
    private static final String       SUBSCRIPTION_TEMPLATE = """
            {
              "subject": {
                "username": "%s",
                "birthday" : "%s"
              },
              "action": "read book",
              %s
              "environment": null
            }""";
    private static final String       SUB_BOB_BOOK3         = String.format(SUBSCRIPTION_TEMPLATE, "bob",
            birthdayForAgeInYears(10), BOOK3);
    private static final String       SUB_BOB_BOOK4         = String.format(SUBSCRIPTION_TEMPLATE, "bob",
            birthdayForAgeInYears(10), BOOK4);

    @Test
    void tutorialAgeCheckScenario() throws JsonProcessingException, InitializationException {
        final var policySet = """
                import time.timeBetween
                import time.dateOf
                import time.now
                import filter.blacken

                set "check age set"
                first-applicable
                for action == "read book"
                var birthday    = subject.birthday;
                var today       = dateOf(|<now>);
                var age         = timeBetween(birthday, today, "years");

                policy "check age transform set"
                permit
                where
                    age < resource.ageRating;
                obligation {
                            "type": "logAccess",
                            "message": "Attention, "+subject.username+" accessed the book '"+resource.name+"'."
                           }
                transform
                    resource |- {
                        @.content : blacken(3,0,"\u2588")
                    }

                policy "check age compact set"
                permit
                    age >= resource.ageRating
                """;

        final var subBobBook3 = MAPPER.readValue(SUB_BOB_BOOK3, AuthorizationSubscription.class);
        final var subBobBook4 = MAPPER.readValue(SUB_BOB_BOOK4, AuthorizationSubscription.class);
        final var pdp         = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policySet),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        final var decisionBook3 = pdp.decide(subBobBook3).blockFirst();
        assertThat(decisionBook3.getObligations()).isEmpty();
        final var decisionBook4 = pdp.decide(subBobBook4).blockFirst();
        assertThat(decisionBook4.getObligations()).isNotEmpty();
    }

    private static String birthdayForAgeInYears(int age) {
        return LocalDate.now().minusYears(age).minusDays(20).toString();
    }

}
