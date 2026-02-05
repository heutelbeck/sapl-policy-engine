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
package io.sapl.node.auth;

import io.sapl.api.SaplVersion;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents an authenticated SAPL user with their associated PDP identifier.
 *
 * @param id the unique identifier for the user
 * @param pdpId the PDP instance identifier this user should be routed to
 */
public record SaplUser(String id, String pdpId) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String DEFAULT_PDP_ID = "default";
    private static final String ERROR_ID_NULL_OR_BLANK = "User id must not be null or blank";

    /**
     * Creates a SaplUser with the given id and pdpId.
     * If pdpId is null or blank, defaults to "default".
     *
     * @param id the unique identifier for the user
     * @param pdpId the PDP instance identifier, or null/blank for default
     */
    public SaplUser {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(ERROR_ID_NULL_OR_BLANK);
        }
        if (pdpId == null || pdpId.isBlank()) {
            pdpId = DEFAULT_PDP_ID;
        }
    }

    /**
     * Creates a SaplUser with the default PDP ID.
     *
     * @param id the unique identifier for the user
     * @return a new SaplUser with default PDP ID
     */
    public static SaplUser withDefaultPdpId(String id) {
        return new SaplUser(id, DEFAULT_PDP_ID);
    }

}
