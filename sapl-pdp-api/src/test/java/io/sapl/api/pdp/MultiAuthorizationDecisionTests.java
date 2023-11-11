/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MultiAuthorizationDecisionTests {

    private static final String SUBSCRIPTION_ID = "SUBSCRIPTION-ID";

    private static final String MULTI_AUTHORIZATION_DECISION = "MultiAuthorizationDecision";

    private static final String ID = "ID";

    @Test
    void emptyDecisionToStringTest() {
        var string = new MultiAuthorizationDecision().toString();
        assertAll(() -> assertThat(string, startsWith(MULTI_AUTHORIZATION_DECISION)),
                () -> assertThat(string, not(containsString(SUBSCRIPTION_ID))));
    }

    @Test
    void filledDecisionToStringTest() {
        var decision = new MultiAuthorizationDecision();
        decision.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.PERMIT);
        var string = decision.toString();
        assertAll(() -> assertThat(string, startsWith(MULTI_AUTHORIZATION_DECISION)),
                () -> assertThat(string, containsString(SUBSCRIPTION_ID + ": " + ID)));
    }

    @Test
    void isPermittedTrueTest() {
        var decision = new MultiAuthorizationDecision();
        decision.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.PERMIT);
        assertTrue(decision.isAccessPermittedForSubscriptionWithId(ID));
    }

    @Test
    void isPermittedFalseTest() {
        var decision = new MultiAuthorizationDecision();
        decision.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.INDETERMINATE);
        assertFalse(decision.isAccessPermittedForSubscriptionWithId(ID));
    }

    @Test
    void isPermittedFalseNoEntryTest() {
        var decision = new MultiAuthorizationDecision();
        assertFalse(decision.isAccessPermittedForSubscriptionWithId(ID));
    }

    @Test
    void isPermittedNullTest() {
        var decision = new MultiAuthorizationDecision();
        assertThrows(NullPointerException.class, () -> decision.isAccessPermittedForSubscriptionWithId(null));
    }

    @Test
    void getDecisionForSubscriptionWithIdTest() {
        var decision = new MultiAuthorizationDecision();
        decision.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.PERMIT);
        assertEquals(Decision.PERMIT, decision.getDecisionForSubscriptionWithId(ID));
    }

    @Test
    void sizeTest() {
        var decision = new MultiAuthorizationDecision();
        decision.setAuthorizationDecisionForSubscriptionWithId(ID, AuthorizationDecision.PERMIT);
        assertEquals(1, decision.size());
    }

    @Test
    void getDecisionForSubscriptionWithIdNullTest() {
        var decision = new MultiAuthorizationDecision();
        assertThat(decision.getDecisionForSubscriptionWithId(ID), is(nullValue()));
    }

    @Test
    void getDecisionForSubscriptionWithIdNullParamTest() {
        var decision = new MultiAuthorizationDecision();
        assertThrows(NullPointerException.class, () -> decision.getDecisionForSubscriptionWithId(null));
    }

    @Test
    void getAuthorizationDecisionForSubscriptionWithIdNullParamTest() {
        var decision = new MultiAuthorizationDecision();
        assertThrows(NullPointerException.class, () -> decision.getAuthorizationDecisionForSubscriptionWithId(null));
    }

    @Test
    void setAuthorizationDecisionForSubscriptionWithIdNullParamTest() {
        var decision = new MultiAuthorizationDecision();
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> decision.setAuthorizationDecisionForSubscriptionWithId(null, AuthorizationDecision.DENY)),
                () -> assertThrows(NullPointerException.class,
                        () -> decision.setAuthorizationDecisionForSubscriptionWithId(ID, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> decision.setAuthorizationDecisionForSubscriptionWithId(null, null)));
    }

    @Test
    void indeterminateDefaultDecisionTest() {
        var decision = MultiAuthorizationDecision.indeterminate();
        assertEquals(Decision.INDETERMINATE, decision.getAuthorizationDecisionForSubscriptionWithId("").getDecision());
    }

}
