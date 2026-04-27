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
package io.sapl.spring.pep.constraints.providers;

import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.DECISION_SIGNAL_TYPE;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.MAPPER;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.OUTPUT_LIST_TYPE;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.expectList;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.permitWith;
import static io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.theUsualSuspects;
import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.spring.pep.constraints.providers.ContentFilterTestSupport.WatchCitizen;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;
import lombok.val;

/**
 * Tests that exercise both content-filter providers together. Concerns scoped
 * here: priority ordering between the predicate filter and the content
 * redaction filter, both as a behavioural outcome and as a numeric invariant
 * on the resolved handlers.
 */
@DisplayName("Content filter provider interactions")
class ContentFilterProviderInteractionTests {

    private final EnforcementPlanner planner = new EnforcementPlanner(
            List.of(new ContentFilteringProvider(MAPPER), new ContentFilterPredicateProvider(MAPPER)), MAPPER);

    private static Value parse(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @Test
    @DisplayName("Predicate filter sees the original payload and runs before content redaction")
    void givenPredicateAndContentFilterThenPredicateRunsFirst() {
        val                decision   = permitWith("""
                [
                  { "type": "jsonContentFilterPredicate",
                    "conditions": [{"path": "$.guild", "type": "==", "value": "Watch"}] },
                  { "type": "filterJsonContent",
                    "actions": [{"path": "$.address", "type": "blacken"}] }
                ]
                """);
        val                plan       = planner.plan(decision, Set.of(OUTPUT_LIST_TYPE, DECISION_SIGNAL_TYPE));
        val                result     = plan.execute(OutputSignal.of(List.class, theUsualSuspects()), false);
        List<WatchCitizen> outputList = expectList(result);

        assertThat(outputList).hasSize(2).extracting(WatchCitizen::name).containsExactly("Sam Vimes",
                "Carrot Ironfoundersson");
        assertThat(outputList).allSatisfy(citizen -> assertThat(citizen.address()).matches("█+"));
    }

    @Test
    @DisplayName("Predicate filter priority is strictly less than content filter priority")
    void givenBothProvidersThenPredicatePriorityIsLowerThanContentFiltering() {
        val contentProvider   = new ContentFilteringProvider(MAPPER);
        val predicateProvider = new ContentFilterPredicateProvider(MAPPER);
        val signals           = Set.of(OUTPUT_LIST_TYPE);

        val contentScoped   = contentProvider.getConstraintHandlers(parse("""
                {"type": "filterJsonContent"}
                """), signals).getFirst();
        val predicateScoped = predicateProvider.getConstraintHandlers(parse("""
                {"type": "jsonContentFilterPredicate"}
                """), signals).getFirst();

        assertThat(predicateScoped.priority()).isLessThan(contentScoped.priority());
    }
}
