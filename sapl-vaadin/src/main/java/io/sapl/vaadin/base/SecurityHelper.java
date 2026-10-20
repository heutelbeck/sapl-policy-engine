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
package io.sapl.vaadin.base;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityHelper {
    public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /**
     * This method reads the name of the user who is logged in.
     *
     * @return the name of the user
     */
    public static String getUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getName();
        } else {
            return null;
        }
    }

    /**
     * This method reads the roles of the user who is logged in.
     *
     * @return the roles of the user as a list
     */
    public static List<String> getUserRoles() {
        Authentication userAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (userAuthentication != null) {
            // .toList() breaks code, must use .collect
            return userAuthentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    public static ObjectNode getSubject() {
        var subject = JSON.objectNode();
        subject.put("username", SecurityHelper.getUsername());
        var rolesNode = JSON.arrayNode();
        for (String role : SecurityHelper.getUserRoles()) {
            rolesNode.add(role);
        }
        subject.set("roles", rolesNode);
        return subject;
    }
}
