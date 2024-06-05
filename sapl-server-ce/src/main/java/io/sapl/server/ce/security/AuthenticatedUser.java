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
package io.sapl.server.ce.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.RequestEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.vaadin.flow.server.VaadinServletRequest;

import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1074340640694624737L;

    // In a multi-provider scenario this parameter has to be replaced by a more
    // generic
    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:#{null}}")
    private String keycloakIssuerUri;

    public Optional<UserDetails> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if the user need an OAuth2 implementation
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            return Optional.of(new UserDetails() {

                private static final long serialVersionUID = 172015643654124086L;

                @Override
                public Collection<? extends GrantedAuthority> getAuthorities() {
                    return oauth2User.getAuthorities();
                }

                @Override
                public String getPassword() {
                    return null;
                }

                @Override
                public String getUsername() {
                    return oauth2User.getName();
                }

                @Override
                public boolean isAccountNonExpired() {
                    return true;
                }

                @Override
                public boolean isAccountNonLocked() {
                    return true;
                }

                @Override
                public boolean isCredentialsNonExpired() {
                    return true;
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            });
        } else if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return Optional.of(userDetails);
        }
        return Optional.empty();
    }

    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getPrincipal() instanceof OidcUser user) {
            String endSessionEndpoint = keycloakIssuerUri + "/protocol/openid-connect/logout";

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                    .queryParam("id_token_hint", user.getIdToken().getTokenValue());

            RestTemplate restTemplate = new RestTemplate();
            try {
                RequestEntity<Void> requestEntity = RequestEntity.get(builder.build().toUri()).build();
                restTemplate.exchange(requestEntity, Void.class);
            } catch (Exception e) {
                log.error("Error during closing of Keycloak-Session", e);
            }
        }
        // Invalidate the session in Vaadin
        VaadinServletRequest.getCurrent().getHttpServletRequest().getSession().invalidate();
    }

}
