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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.NonNull;

/**
 * Represents an authorization subscription containing subject, action,
 * resource, and environment attributes for policy evaluation.
 * <p>
 * Each authorization subscription consists of:
 * <ul>
 * <li>a subject describing the entity which is requesting permission</li>
 * <li>an action describing for which activity the subject is requesting
 * permission</li>
 * <li>a resource describing or even containing the resource for which the
 * subject is requesting the permission to execute the action</li>
 * <li>an environment object describing additional contextual information from
 * the environment which may be required for evaluating the underlying
 * policies</li>
 * </ul>
 * <p>
 * Objects are marshaled using the Jackson ObjectMapper. If omitted, a default
 * mapper is used. A custom mapper can be supplied.
 */
public record AuthorizationSubscription(
        @NonNull Value subject,
        @NonNull Value action,
        @NonNull Value resource,
        @NonNull Value environment) {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

    /**
     * Creates an AuthorizationSubscription with the given subject, action, and
     * resource. Environment defaults to UNDEFINED. Objects are marshaled using a
     * default ObjectMapper with Jdk8Module registered.
     *
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @return new AuthorizationSubscription
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource) {
        return of(subject, action, resource, DEFAULT_MAPPER);
    }

    /**
     * Creates an AuthorizationSubscription with the given subject, action, and
     * resource. Environment defaults to UNDEFINED. Objects are marshaled using the
     * supplied ObjectMapper.
     *
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param mapper the ObjectMapper to be used for marshaling
     * @return new AuthorizationSubscription
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource, ObjectMapper mapper) {
        return of(subject, action, resource, null, mapper);
    }

    /**
     * Creates an AuthorizationSubscription with all four attributes. Objects are
     * marshaled using a default ObjectMapper with Jdk8Module registered.
     *
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param environment an object describing the environment (null becomes
     * UNDEFINED)
     * @return new AuthorizationSubscription
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource, Object environment) {
        return of(subject, action, resource, environment, DEFAULT_MAPPER);
    }

    /**
     * Creates an AuthorizationSubscription with all four attributes. Objects are
     * marshaled using the supplied ObjectMapper.
     *
     * @param subject an object describing the subject
     * @param action an object describing the action
     * @param resource an object describing the resource
     * @param environment an object describing the environment (null becomes
     * UNDEFINED)
     * @param mapper the ObjectMapper to be used for marshaling
     * @return new AuthorizationSubscription
     */
    public static AuthorizationSubscription of(Object subject, Object action, Object resource, Object environment,
            ObjectMapper mapper) {
        return new AuthorizationSubscription(toValue(subject, mapper), toValue(action, mapper),
                toValue(resource, mapper), environment == null ? Value.UNDEFINED : toValue(environment, mapper));
    }

    private static Value toValue(Object object, ObjectMapper mapper) {
        if (object == null) {
            return Value.UNDEFINED;
        }
        if (object instanceof Value value) {
            return value;
        }
        if (object instanceof JsonNode jsonNode) {
            return ValueJsonMarshaller.fromJsonNode(jsonNode);
        }
        return ValueJsonMarshaller.fromJsonNode(mapper.valueToTree(object));
    }
}
