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
package io.sapl.server.ce.security;

import com.heutelbeck.uuid.Base64Id;
import io.sapl.server.ce.model.clients.AuthType;
import io.sapl.server.ce.model.clients.ClientCredentials;
import io.sapl.server.ce.model.clients.ClientCredentialsRepository;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.apikey.ApiKeyService;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collection;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class ClientDetailsService implements UserDetailsService {

    public static final String  CLIENT = "SAPL_CLIENT";
    public static final String  ADMIN  = "ADMIN";
    private final ApiKeyService apiKeyService;

    @Value("${io.sapl.server.accesscontrol.admin-username:#{null}}")
    private String                            adminUsername;
    @Value("${io.sapl.server.accesscontrol.encoded-admin-password:#{null}}")
    private String                            encodedAdminPassword;
    private final ClientCredentialsRepository clientCredentialsRepository;
    private final PasswordEncoder             passwordEncoder;

    @PostConstruct
    void validateSecuritySettings() {
        if (adminUsername == null) {
            log.error(
                    "Admin username undefined. To define the username, specify it in the property 'io.sapl.server.accesscontrol.admin-username'.");
        }
        if (encodedAdminPassword == null) {
            log.error(
                    "Admin password undefined. To define the password, specify it in the property 'io.sapl.server.accesscontrol.encoded-admin-password'. The password is expected in encoded form using Argon2.");
        }
        if (adminUsername == null || encodedAdminPassword == null) {
            throw new IllegalStateException("Administrator credentials missing.");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (adminUsername.equals(username)) {
            return org.springframework.security.core.userdetails.User.withUsername(adminUsername)
                    .password(encodedAdminPassword).roles(ADMIN).build();
        }

        final var clientCredentials = clientCredentialsRepository.findByKey(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("client credentials with key \"%s\" not found", username)));

        return org.springframework.security.core.userdetails.User.withUsername(clientCredentials.getKey())
                .password(clientCredentials.getEncodedSecret()).authorities(CLIENT).build();
    }

    public Collection<ClientCredentials> getAll() {
        return clientCredentialsRepository.findAll();
    }

    public long getAmount() {
        return clientCredentialsRepository.count();
    }

    /**
     * Generates a random key with key length of 256 bit encoded in base64
     *
     * @return base64 encoded secret
     */
    private String generateSecret() {
        return new Base64StringKeyGenerator(32).generateKey();
    }

    public Tuple2<ClientCredentials, String> createBasicDefault() {
        final var key               = Base64Id.randomID();
        final var secret            = generateSecret();
        final var clientCredentials = clientCredentialsRepository
                .save(new ClientCredentials(key, AuthType.BASIC, encodeSecret(secret)));
        return Tuples.of(clientCredentials, secret);
    }

    public String createApiKeyDefault() {
        // apiKey needs to be a combination of <key>_<secret> to identify the client in
        // the authentication process. We need to avoid underscores in the key value.
        final var key    = Base64Id.randomID().replace('_', '-');
        final var apiKey = "sapl_" + key + "_" + generateSecret();
        clientCredentialsRepository.save(new ClientCredentials(key, AuthType.APIKEY, encodeSecret(apiKey)));
        return apiKey;
    }

    public void delete(@NonNull ClientCredentials clientCredential) {
        clientCredentialsRepository.deleteById(clientCredential.getId());
        if (clientCredential.getAuthType().equals(AuthType.APIKEY)) {
            apiKeyService.removeFromCache(clientCredential.getKey());
        }
    }

    public String encodeSecret(@NonNull String secret) {
        return passwordEncoder.encode(secret);
    }
}
