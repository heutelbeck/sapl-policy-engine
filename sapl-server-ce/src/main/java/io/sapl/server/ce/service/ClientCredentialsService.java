/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.google.common.collect.Iterables;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.persistence.ClientCredentialsRepository;
import io.sapl.server.ce.security.SecurityConfiguration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientCredentialsService implements UserDetailsService, Serializable {
	private final ClientCredentialsRepository clientCredentialsRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Collection<ClientCredentials> clientCredentialsWithKey = clientCredentialsRepository.findByKey(username);
		if (clientCredentialsWithKey.isEmpty()) {
			throw new UsernameNotFoundException(
					String.format("client credentials with key \"%s\" not found", username));
		}

		if (clientCredentialsWithKey.size() > 1) {
			log.warn("more than one client credentials with key \"{}\" not existing", username);
		}

		ClientCredentials relevantClientCredentials = Iterables.get(clientCredentialsWithKey, 0);

		String encodedPassword = relevantClientCredentials.getEncodedSecret();

		// @formatter:off
		return org.springframework.security.core.userdetails.User
				.withUsername(relevantClientCredentials.getKey())
				.password(encodedPassword)
				.roles(SecurityConfiguration.PDP_CLIENT_ROLE)
				.build();
		// @formatter:on
	}

	/**
	 * Gets all {@link ClientCredentials}s.
	 * 
	 * @return the instances
	 */
	public Collection<ClientCredentials> getAll() {
		return clientCredentialsRepository.findAll();
	}

	/**
	 * Gets the amount of {@link ClientCredentials}s.
	 * 
	 * @return the amount
	 */
	public long getAmount() {
		return clientCredentialsRepository.count();
	}

	/**
	 * Creates a new {@link ClientCredentials} with generated values for key and
	 * secret.
	 * 
	 * @return a tuple containing the created {@link ClientCredentials} and the non
	 *         encoded secret
	 */
	public Tuple2<ClientCredentials, String> createDefault() {
		String secret = generateFormattedUuid();

		ClientCredentials clientCredentialsToCreate = new ClientCredentials();
		clientCredentialsToCreate.setKey(generateFormattedUuid());
		clientCredentialsToCreate.setEncodedSecret(encodeSecret(secret));

		ClientCredentials createdClientCredentials = clientCredentialsRepository.save(clientCredentialsToCreate);

		log.info("created client credentials: key = {}", createdClientCredentials.getKey());

		return Tuples.of(createdClientCredentials, secret);
	}

	/**
	 * Deletes a specific {@link ClientCredentials}.
	 * 
	 * @param id the id of the {@link ClientCredentials} to delete
	 */
	public void delete(@NonNull Long id) {
		clientCredentialsRepository.deleteById(id);
	}

	private static String generateFormattedUuid() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private String encodeSecret(@NonNull String secret) {
		return passwordEncoder.encode(secret);
	}
}
