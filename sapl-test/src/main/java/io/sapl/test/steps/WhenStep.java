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
package io.sapl.test.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;

/**
 * When Step in charge of setting the {@link AuthorizationSubscription} for the
 * test case. Next Step available : {@link ExpectStep}
 */
public interface WhenStep {

    /**
     * Sets the {@link AuthorizationSubscription} for the test case.
     * 
     * @param authzSubscription the {@link AuthorizationSubscription}
     * @return next available Step {@link ExpectStep}
     */
    ExpectStep when(AuthorizationSubscription authzSubscription);

    /**
     * Sets the {@link AuthorizationSubscription} for the test case.
     * 
     * @param jsonAuthzSub {@link String} containing JSON defining a
     *                     {@link AuthorizationSubscription}
     * @return next available Step {@link ExpectStep}
     * @throws JsonProcessingException thrown if JSON parsing fails
     */
    ExpectStep when(String jsonAuthzSub) throws JsonProcessingException;

    /**
     * Sets the {@link AuthorizationSubscription} for the test case.
     * 
     * @param jsonNode {@link com.fasterxml.jackson.databind.node.ObjectNode}
     *                 defining a {@link AuthorizationSubscription}
     * @return next available Step {@link ExpectStep}
     */
    ExpectStep when(JsonNode jsonNode);

}
