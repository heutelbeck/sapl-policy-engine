package io.sapl.server.ce.persistence;

import java.util.Collection;

import org.springframework.data.repository.CrudRepository;

import io.sapl.server.ce.model.ClientCredentials;

public interface ClientCredentialsRepository extends CrudRepository<ClientCredentials, Long> {
	/**
	 * Returns all instances of the {@link ClientCredentials}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<ClientCredentials> findAll();
}
