package io.sapl.server.ce.service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

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
		clientCredentialsToCreate.setSecret(hashSecret(UUID.randomUUID().toString()));

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
			clientCredentialsToEdit.setSecret(hashSecret(secret));
		}

		clientCredentialsRepository.save(clientCredentialsToEdit);
	}

	/**
	 * Deletes a specific {@link ClientCredentials}.
	 * 
	 * @param id the id of the {@link ClientCredentials} to delete
	 */
	public void delete(@NonNull Long id) {
		this.clientCredentialsRepository.deleteById(id);
	}

	private String hashSecret(@NonNull String secret) {
		return Hashing.sha512().hashString(secret, StandardCharsets.UTF_8).toString();
	}
}
