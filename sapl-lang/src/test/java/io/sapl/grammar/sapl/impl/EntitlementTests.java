/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.impl.util.ParserUtil;

class EntitlementTests {

    @Test
    void permitIsPermit() throws IOException {
        var permit = ParserUtil.entitlement("permit");
        assertEquals(Decision.PERMIT, permit.getDecision());
    }

    @Test
    void denyIsDeny() throws IOException {
        var deny = ParserUtil.entitlement("deny");
        assertEquals(Decision.DENY, deny.getDecision());
    }

}
