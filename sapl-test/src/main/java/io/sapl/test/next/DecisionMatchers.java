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
package io.sapl.test.next;

import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;

/**
 * Static factory methods for creating {@link DecisionMatcher} instances.
 * <p>
 * Import statically for fluent usage:
 *
 * <pre>{@code
 * import static io.sapl.test.next.DecisionMatchers.*;
 *
 * fixture.whenDecide(subscription)
 *        .expectDecisionMatches(isPermit().containsObligation(logObligation))
 *        .verify();
 * }</pre>
 * <p>
 * Each matcher can be chained with additional constraints:
 * <ul>
 * <li>{@code containsObligation(Value)} - requires obligation to be
 * present</li>
 * <li>{@code containsObligations(Value...)} - requires all obligations
 * present</li>
 * <li>{@code containsAdvice(Value)} - requires advice to be present</li>
 * <li>{@code withResource(Value)} - requires exact resource match</li>
 * </ul>
 */
@UtilityClass
public class DecisionMatchers {

    /**
     * Creates a matcher for PERMIT decisions.
     *
     * @return a matcher expecting Decision.PERMIT
     */
    public static DecisionMatcher isPermit() {
        return new DecisionMatcher(Decision.PERMIT);
    }

    /**
     * Creates a matcher for DENY decisions.
     *
     * @return a matcher expecting Decision.DENY
     */
    public static DecisionMatcher isDeny() {
        return new DecisionMatcher(Decision.DENY);
    }

    /**
     * Creates a matcher for INDETERMINATE decisions.
     *
     * @return a matcher expecting Decision.INDETERMINATE
     */
    public static DecisionMatcher isIndeterminate() {
        return new DecisionMatcher(Decision.INDETERMINATE);
    }

    /**
     * Creates a matcher for NOT_APPLICABLE decisions.
     *
     * @return a matcher expecting Decision.NOT_APPLICABLE
     */
    public static DecisionMatcher isNotApplicable() {
        return new DecisionMatcher(Decision.NOT_APPLICABLE);
    }

    /**
     * Creates a matcher for a specific decision type.
     *
     * @param decision the expected decision type
     * @return a matcher expecting the specified decision
     */
    public static DecisionMatcher isDecision(Decision decision) {
        return new DecisionMatcher(decision);
    }
}
