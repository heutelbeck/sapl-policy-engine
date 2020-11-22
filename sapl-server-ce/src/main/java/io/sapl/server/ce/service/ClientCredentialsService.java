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

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.google.common.hash.Hashing;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.persistence.ClientCredentialsRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientCredentialsService {
	private final ClientCredentialsRepository clientCredentialsRepository;

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
		clientCredentialsToCreate
				.setHashedSecret(hashSecret(UUID.randomUUID().toString(), clientCredentialsToCreate.getKey()));

		ClientCredentials createdClientCredentials = clientCredentialsRepository.save(clientCredentialsToCreate);

		log.info(String.format("created client credentials: key = %s", createdClientCredentials.getKey()));

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
			clientCredentialsToEdit.setHashedSecret(hashSecret(secret, key));
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

	/**
	 * Generates a {@link String} representing a hash code for a specified secret
	 * 
	 * @param secret the secret to hash
	 * @param key    the client key of the credentials as salt
	 * @return the {@link String} representing the hash code
	 */
	private String hashSecret(@NonNull String secret, @NonNull String key) {
		return Hashing.sha512().hashString(secret + key, StandardCharsets.UTF_8).toString();
	}
}
