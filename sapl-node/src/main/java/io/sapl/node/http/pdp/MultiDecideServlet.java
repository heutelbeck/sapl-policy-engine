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
package io.sapl.node.http.pdp;

import static io.sapl.node.MultiSubscriptionLimits.DEFAULT_MAX_MULTI_SUBSCRIPTION_COUNT;
import static io.sapl.node.MultiSubscriptionLimits.exceededMessage;
import static io.sapl.node.MultiSubscriptionLimits.exceedsMaxCount;
import static io.sapl.node.MultiSubscriptionLimits.requirePositiveMax;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.stream.Stream;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bypass-Spring servlet for {@code POST /api/pdp/multi-decide}:
 * server-sent-event stream of per-subscription
 * {@link IdentifiableAuthorizationDecision} values.
 */
public final class MultiDecideServlet
        extends SseStreamServlet<MultiAuthorizationSubscription, IdentifiableAuthorizationDecision> {

    private final BlockingPolicyDecisionPoint pdp;
    private final int                         maxMultiSubscriptionCount;

    /**
     * Creates a servlet with the default multi-subscription entry limit.
     *
     * @param pdp the blocking PDP
     * @param authHandler the HTTP authentication handler
     * @param mapper the JSON mapper
     * @param keepAliveInterval the SSE keep-alive interval
     * @param keepAliveScheduler scheduler for keep-alive frames
     * @param pumpExecutor executor for stream pumps
     * @param connectionRegistry registry for active SSE connections
     */
    public MultiDecideServlet(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler,
            JsonMapper mapper,
            Duration keepAliveInterval,
            ScheduledExecutorService keepAliveScheduler,
            ExecutorService pumpExecutor,
            SseConnectionRegistry connectionRegistry) {
        this(pdp, authHandler, mapper, keepAliveInterval, keepAliveScheduler, pumpExecutor, connectionRegistry,
                DEFAULT_MAX_MULTI_SUBSCRIPTION_COUNT);
    }

    /**
     * Creates a servlet with a configured multi-subscription entry limit.
     *
     * @param pdp the blocking PDP
     * @param authHandler the HTTP authentication handler
     * @param mapper the JSON mapper
     * @param keepAliveInterval the SSE keep-alive interval
     * @param keepAliveScheduler scheduler for keep-alive frames
     * @param pumpExecutor executor for stream pumps
     * @param connectionRegistry registry for active SSE connections
     * @param maxMultiSubscriptionCount maximum entries accepted per
     * multi-subscription
     */
    public MultiDecideServlet(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler,
            JsonMapper mapper,
            Duration keepAliveInterval,
            ScheduledExecutorService keepAliveScheduler,
            ExecutorService pumpExecutor,
            SseConnectionRegistry connectionRegistry,
            int maxMultiSubscriptionCount) {
        super(authHandler, mapper, keepAliveInterval, keepAliveScheduler, pumpExecutor, connectionRegistry);
        this.pdp                       = pdp;
        this.maxMultiSubscriptionCount = requirePositiveMax(maxMultiSubscriptionCount);
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
    protected boolean acceptSubscription(MultiAuthorizationSubscription subscription, HttpServletResponse response)
            throws IOException {
        if (!exceedsMaxCount(subscription, maxMultiSubscriptionCount)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                exceededMessage(subscription, maxMultiSubscriptionCount));
        return false;
    }

    @Override
    protected IdentifiableAuthorizationDecision indeterminate() {
        return IdentifiableAuthorizationDecision.INDETERMINATE;
    }
}
