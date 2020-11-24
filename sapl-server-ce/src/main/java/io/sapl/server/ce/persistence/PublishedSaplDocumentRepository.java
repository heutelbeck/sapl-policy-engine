package io.sapl.server.ce.persistence;

import java.io.Serializable;
import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocument;

/**
 * Interface for a repository for accessing persisted
 * {@link PublishedSaplDocument}.
 */
@Repository
public interface PublishedSaplDocumentRepository extends CrudRepository<PublishedSaplDocument, Long>, Serializable {
	/**
	 * Returns all instances of the {@link SaplDocument}s.
	 * 
	 * @return the instances
	 */
	@Override
	Collection<PublishedSaplDocument> findAll();

	/**
	 * Gets the {@link PublishedSaplDocument}s with a specific name.
	 * 
	 * @param name the name
	 * @return the {@link PublishedSaplDocument}s
	 */
	@Query(value = "SELECT s FROM PublishedSaplDocument s WHERE s.name = :name")
	Collection<PublishedSaplDocument> findByName(@Param(value = "name") String name);
}
