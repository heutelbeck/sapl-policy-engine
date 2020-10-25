package io.sapl.server.ce.persistence;

import java.util.Collection;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.pdpconfiguration.Variable;

@Repository
public interface VariablesRepository extends CrudRepository<Variable, Long> {
	/**
	 * Returns all instances of the {@link Variable}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<Variable> findAll();

	/**
	 * Gets the {@link Variable} with a specific name.
	 * 
	 * @param name the name
	 * @return the relevant {@link Variable} instances
	 */
	Collection<Variable> findByName(String name);
}
