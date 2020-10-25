package io.sapl.server.ce.persistence;

import java.util.Collection;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.model.pdpconfiguration.SelectedCombiningAlgorithm;

/**
 * Interface for a repository for accessing the selected
 * {@link PolicyDocumentCombiningAlgorithm}.
 */
@Repository
public interface SelectedCombiningAlgorithmRepository extends CrudRepository<SelectedCombiningAlgorithm, Long> {
	/**
	 * Returns all instances of the {@link SelectedCombiningAlgorithm}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<SelectedCombiningAlgorithm> findAll();
}
