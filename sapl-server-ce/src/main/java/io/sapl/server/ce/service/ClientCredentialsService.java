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

import com.google.common.collect.Iterables;
import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.persistence.ClientCredentialsRepository;
import io.sapl.server.ce.security.SecurityConfiguration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientCredentialsService implements UserDetailsService, Serializable {
	private final ClientCredentialsRepository clientCredentialsRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Collection<ClientCredentials> clientCredentialsWithKey = this.clientCredentialsRepository.findByKey(username);
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
	 * @return the created {@link ClientCredentials}
	 */
	public ClientCredentials createDefault() {
		ClientCredentials clientCredentialsToCreate = new ClientCredentials();
		clientCredentialsToCreate.setKey(UUID.randomUUID().toString());
		clientCredentialsToCreate.setEncodedSecret(encodeSecret(UUID.randomUUID().toString()));

		ClientCredentials createdClientCredentials = clientCredentialsRepository.save(clientCredentialsToCreate);

		log.info("created client credentials: key = {}", createdClientCredentials.getKey());

		return createdClientCredentials;
	}

	/**
	 * Edits a specific {@link ClientCredentials}.
	 * 
	 * @param id     the id of the {@link ClientCredentials} to edit
	 * @param key    the client key to store
	 * @param secret the secret to store (<b>null</b> if no edit is intended)
	 */
	public void edit(@NonNull Long id, @NonNull String key, String secret) {
		Optional<ClientCredentials> optionalClientCredentials = clientCredentialsRepository.findById(id);
		if (optionalClientCredentials.isEmpty()) {
			throw new IllegalArgumentException(String.format("client credentials with id %d was not found", id));
		}

		ClientCredentials clientCredentialsToEdit = optionalClientCredentials.get();
		clientCredentialsToEdit.setKey(key);

		if (secret != null) {
			clientCredentialsToEdit.setEncodedSecret(encodeSecret(secret));
		}

		try {
			clientCredentialsRepository.save(clientCredentialsToEdit);
		} catch (DataIntegrityViolationException ex) {
			throw new IllegalArgumentException(
					"The provided credentials are invalid (e.g. referenced already used key).", ex);
		}
	}

	/**
	 * Deletes a specific {@link ClientCredentials}.
	 * 
	 * @param id the id of the {@link ClientCredentials} to delete
	 */
	public void delete(@NonNull Long id) {
		this.clientCredentialsRepository.deleteById(id);
	}

	private String encodeSecret(@NonNull String secret) {
		return passwordEncoder.encode(secret);
	}
}
