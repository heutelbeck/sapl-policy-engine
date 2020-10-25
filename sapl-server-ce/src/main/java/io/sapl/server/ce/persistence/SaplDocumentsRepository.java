package io.sapl.server.ce.persistence;

import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.sapldocument.SaplDocument;

/**
 * Interface for a repository for accessing persisted {@link SaplDocument}.
 */
@Repository
public interface SaplDocumentsRepository extends CrudRepository<SaplDocument, Long> {
	/**
	 * Returns all instances of the {@link SaplDocument}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<SaplDocument> findAll();

	/**
	 * Gets the {@link SaplDocument}s with a published version with a specific name.
	 * 
	 * @param name the name
	 * @return the {@link SaplDocument}s
	 */
	@Query(value = "SELECT s FROM SaplDocument s INNER JOIN s.publishedVersion v WHERE v.name = :name")
	Collection<SaplDocument> getSaplDocumentsByNameOfPublishedVersion(@Param(value = "name") String name);
}
