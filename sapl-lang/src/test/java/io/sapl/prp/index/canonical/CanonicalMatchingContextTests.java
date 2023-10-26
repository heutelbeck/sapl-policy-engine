/*
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
package io.sapl.prp.index.canonical;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CanonicalMatchingContextTests {

    @Test
    void test_is_referenced() {
        var candidates = new Bitmask();
        var predicate  = new Predicate(new Bool(true));

        var matchingCtx = new CanonicalIndexMatchingContext(0);

        assertFalse(matchingCtx.isPredicateReferencedInCandidates(predicate));

        predicate.getConjunctions().set(5);
        assertFalse(matchingCtx.isPredicateReferencedInCandidates(predicate));

        candidates.set(5);
        matchingCtx.addCandidates(candidates);
        assertTrue(matchingCtx.isPredicateReferencedInCandidates(predicate));
    }

    @Test
    void testAreAllFunctionsEliminated() {
        var matchingCtx = new CanonicalIndexMatchingContext(2);

        matchingCtx.increaseNumberOfEliminatedFormulasForConjunction(0, 42);
        assertThat(matchingCtx.areAllFunctionsEliminated(0, 42), is(true));
        assertThat(matchingCtx.areAllFunctionsEliminated(0, 41), is(false));
    }

}
