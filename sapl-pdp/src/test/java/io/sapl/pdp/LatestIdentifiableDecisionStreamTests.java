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
package io.sapl.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.api.model.Poll;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Latest identifiable decision stream")
class LatestIdentifiableDecisionStreamTests {

    @Test
    @DisplayName("a lagging consumer keeps only the latest pending decision per subscription ID")
    void whenConsumerLagsThenOnlyLatestPendingDecisionPerIdIsRetained() throws Exception {
        try (val stream = new LatestIdentifiableDecisionStream()) {
            stream.put(decision("first", Decision.PERMIT));
            stream.put(decision("first", Decision.DENY));
            stream.put(decision("second", Decision.PERMIT));

            val first  = stream.awaitNext();
            val second = stream.awaitNext();

            assertThat(first).satisfies(decision -> {
                assertThat(decision.subscriptionId()).isEqualTo("first");
                assertThat(decision.decision().decision()).isEqualTo(Decision.DENY);
            });
            assertThat(second).satisfies(decision -> {
                assertThat(decision.subscriptionId()).isEqualTo("second");
                assertThat(decision.decision().decision()).isEqualTo(Decision.PERMIT);
            });
            assertThat(stream.tryNext()).isInstanceOf(Poll.Empty.class);
        }
    }

    private static IdentifiableAuthorizationDecision decision(String subscriptionId, Decision decision) {
        val authorizationDecision = decision == Decision.PERMIT ? AuthorizationDecision.PERMIT
                : AuthorizationDecision.DENY;
        return new IdentifiableAuthorizationDecision(subscriptionId, authorizationDecision);
    }
}
