/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The authorization subscription object defines the tuple of objects
 * constituting a SAPL authorization subscription. Each authorization
 * subscription consists of:
 * <ul>
 * <li>a subject describing the entity which is requesting permission</li>
 * <li>an action describing for which activity the subject is requesting
 * permission</li>
 * <li>a resource describing or even containing the resource for which the
 * subject is requesting the permission to execute the action</li>
 * <li>an environment object describing additional contextual information from
 * the environment which may be required for evaluating the underlying
 * policies.</li>
 * </ul>
 *
 * Are marshaled using the Jackson ObjectMapper. If omitted, a default mapper is
 * used. A custom mapper can be supplied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class AuthorizationSubscription {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

    @NotNull
    private JsonNode subject;

    @NotNull
    private JsonNode action;

    @NotNull
    private JsonNode resource;

    private JsonNode environment;

    /**
     * Creates an AuthorizationSubscription, containing the supplied objects
     * marshaled to JSON by a default ObjectMapper with Jdk8Module registered.
     * Environment will be omitted.
     *
     * @param subject  an object describing the subject.
     * @param action   an object describing the action.
     * @param resource an object describing the resource.
     * @return an AuthorizationSubscription for subscribing to a SAPL PDP
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource) {
        return of(subject, action, resource, MAPPER);
    }

    /**
     * Creates an AuthorizationSubscription, containing the supplied objects
     * marshaled the supplied ObjectMapper. Environment will be omitted.
     *
     * @param subject  an object describing the subject.
     * @param action   an object describing the action.
     * @param resource an object describing the resource.
     * @param mapper   the ObjectMapper to be used for marshaling.
     * @return an AuthorizationSubscription for subscribing to a SAPL PDP
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource, ObjectMapper mapper) {
        return of(subject, action, resource, null, mapper);
    }

    /**
     * Creates an AuthorizationSubscription, containing the supplied objects
     * marshaled to JSON by a default ObjectMapper with Jdk8Module registered.
     *
     * @param subject     an object describing the subject.
     * @param action      an object describing the action.
     * @param resource    an object describing the resource.
     * @param environment an object describing the environment.
     * @return an AuthorizationSubscription for subscribing to a SAPL PDP
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource, Object environment) {
        return of(subject, action, resource, environment, MAPPER);
    }

    /**
     * Creates an AuthorizationSubscription, containing the supplied objects
     * marshaled the supplied ObjectMapper.
     *
     * @param subject     an object describing the subject.
     * @param action      an object describing the action.
     * @param resource    an object describing the resource.
     * @param environment an object describing the environment.
     * @param mapper      the ObjectMapper to be used for marshaling.
     * @return an AuthorizationSubscription for subscribing to a SAPL PDP
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource, Object environment,
            ObjectMapper mapper) {
        return new AuthorizationSubscription(mapper.valueToTree(subject), mapper.valueToTree(action),
                mapper.valueToTree(resource), environment == null ? null : mapper.valueToTree(environment));
    }

}
