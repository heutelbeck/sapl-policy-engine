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
package io.sapl.api.pdp;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BaseJsonNode;
import io.sapl.api.SaplVersion;
import lombok.*;

import java.io.Serializable;
import java.util.Optional;

/**
 * Container for a decision
 */
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonAutoDetect(isGetterVisibility = Visibility.NONE, getterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public class AuthorizationDecision implements Serializable {

    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * A simple PERMIT decision.
     */
    public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT);

    /**
     * A simple DENY decision.
     */
    public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY);

    /**
     * A simple INDETERMINATE decision.
     */
    public static final AuthorizationDecision INDETERMINATE = new AuthorizationDecision(Decision.INDETERMINATE);

    /**
     * A simple NOT_APPLICABLE decision.
     */
    public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE);

    @Getter
    Decision     decision    = Decision.INDETERMINATE;
    BaseJsonNode resource    = null;
    ArrayNode    obligations = null;
    ArrayNode    advice      = null;

    /**
     * @param decision Creates an immutable authorization decision with 'decision'
     * as value, and without any resource, advice, or obligations. Must not be null.
     */
    public AuthorizationDecision(@NonNull Decision decision) {
        this.decision = decision;
    }

    /**
     * Creates an immutable authorization decision.
     *
     * @param decision the Decision
     * @param maybeResource Optional Resource
     * @param maybeObligations Optional Obligations
     * @param maybeAdvice Optional Advice
     */
    public AuthorizationDecision(@NonNull Decision decision,
            @NonNull Optional<JsonNode> maybeResource,
            @NonNull Optional<ArrayNode> maybeObligations,
            @NonNull Optional<ArrayNode> maybeAdvice) {
        this.decision = decision;
        maybeResource.ifPresent(aResource -> this.resource = (BaseJsonNode) aResource);
        maybeObligations
                .ifPresent(someObligations -> this.obligations = someObligations.isEmpty() ? null : someObligations);
        maybeAdvice.ifPresent(someAdvice -> this.advice = someAdvice.isEmpty() ? null : someAdvice);
    }

    /**
     * Get the Resource
     *
     * @return an Optional JsonNode containing a resource object if present.
     */
    public Optional<JsonNode> getResource() {
        return Optional.ofNullable(resource);
    }

    /**
     * Get the Obligations
     *
     * @return an Optional ArrayNode containing obligations if present.
     */
    public Optional<ArrayNode> getObligations() {
        return Optional.ofNullable(obligations);
    }

    /**
     * Get the Advice
     *
     * @return an Optional ArrayNode containing advice if present.
     */
    public Optional<ArrayNode> getAdvice() {
        return Optional.ofNullable(advice);
    }

    /**
     * @param newObligations a JSON array containing obligations.
     * @return new immutable decision object, replacing the obligations of the
     * original object with newObligations. If the array is empty, no obligations
     * will be present, not even an empty array.
     */
    public AuthorizationDecision withObligations(@NonNull ArrayNode newObligations) {
        return new AuthorizationDecision(decision, resource, newObligations.isEmpty() ? null : newObligations, advice);
    }

    /**
     * @param newAdvice a JSON array containing advice.
     * @return new immutable decision object, replacing the advice of the original
     * object with newAdvice. If the array is empty, no advice will be present, not
     * even an empty array.
     */
    public AuthorizationDecision withAdvice(@NonNull ArrayNode newAdvice) {
        return new AuthorizationDecision(decision, resource, obligations, newAdvice.isEmpty() ? null : newAdvice);
    }

    /**
     * @param newResource a JSON object, must nor be null.
     * @return new immutable decision object, replacing the resource with
     * newResource.
     */
    public AuthorizationDecision withResource(@NonNull JsonNode newResource) {
        return new AuthorizationDecision(decision, (BaseJsonNode) newResource, obligations, advice);
    }

    /**
     * @param newDecision a Decision value.
     * @return new immutable decision object, replacing the resource with
     * newResource.
     */
    public AuthorizationDecision withDecision(@NonNull Decision newDecision) {
        return new AuthorizationDecision(newDecision, resource, obligations, advice);
    }

}
