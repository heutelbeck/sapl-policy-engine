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
package io.sapl.node.http.servlet;

import java.io.Serial;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.stream.Stream;
import io.sapl.node.http.auth.HttpAuthHandler;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bypass-Spring servlet for {@code POST /api/pdp/multi-decide}:
 * server-sent-event stream of per-subscription
 * {@link IdentifiableAuthorizationDecision} values.
 */
public final class MultiDecideServlet
        extends SseStreamServlet<MultiAuthorizationSubscription, IdentifiableAuthorizationDecision> {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final BlockingPolicyDecisionPoint pdp;

    public MultiDecideServlet(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler,
            JsonMapper mapper,
            Duration keepAliveInterval,
            ScheduledExecutorService keepAliveScheduler,
            ExecutorService pumpExecutor,
            SseConnectionRegistry connectionRegistry) {
        super(authHandler, mapper, keepAliveInterval, keepAliveScheduler, pumpExecutor, connectionRegistry);
        this.pdp = pdp;
    }

    @Override
    protected Class<MultiAuthorizationSubscription> subscriptionType() {
        return MultiAuthorizationSubscription.class;
    }

    @Override
    protected Stream<IdentifiableAuthorizationDecision> openStream(MultiAuthorizationSubscription subscription,
            String pdpId) {
        return pdp.decide(subscription, pdpId);
    }

    @Override
    protected IdentifiableAuthorizationDecision indeterminate() {
        return IdentifiableAuthorizationDecision.INDETERMINATE;
    }
}
