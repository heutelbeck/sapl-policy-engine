package io.sapl.server.ce.service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.google.common.hash.Hashing;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.model.sapldocument.SaplDocument;
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
		return this.clientCredentialsRepository.findAll();
	}

	/**
	 * Gets the amount of {@link ClientCredentials}s.
	 * 
	 * @return the amount
	 */
	public long getAmount() {
		return this.clientCredentialsRepository.count();
	}

	/**
	 * Creates a new {@link SaplDocument} with a default document value.
	 * 
	 * @return the created {@link SaplDocument}
	 */
	public ClientCredentials createDefault() {
		ClientCredentials clientCredentialsToCreate = new ClientCredentials();
		clientCredentialsToCreate.setKey(UUID.randomUUID().toString());
		clientCredentialsToCreate.setHashedSecret(this.hashSecret(UUID.randomUUID().toString()));

		ClientCredentials createdClientCredentials = this.clientCredentialsRepository.save(clientCredentialsToCreate);

		log.info(String.format("created client credentials: key = %s", createdClientCredentials.getKey()));

		return createdClientCredentials;
	}

	private String hashSecret(@NonNull String secret) {
		return Hashing.sha512().hashString(secret, StandardCharsets.UTF_8).toString();
	}
}
