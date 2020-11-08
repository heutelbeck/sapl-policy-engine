package io.sapl.server.ce.persistence;

import java.util.Collection;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.ClientCredentials;

@Repository
public interface ClientCredentialsRepository extends CrudRepository<ClientCredentials, Long> {
	/**
	 * Returns all instances of the {@link ClientCredentials}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<ClientCredentials> findAll();
}
