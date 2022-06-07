package io.sapl.server.ce.persistence;

import java.io.Serializable;
import java.util.Collection;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;

/**
 * Interface for a repository for accessing persisted
 * {@link PublishedSaplDocument}.
 */
@Repository
public interface PublishedSaplDocumentRepository extends CrudRepository<PublishedSaplDocument, Long>, Serializable {
	@Override
	Collection<PublishedSaplDocument> findAll();

	@Query(value = "SELECT s FROM PublishedSaplDocument s WHERE s.documentName = :documentName")
	Collection<PublishedSaplDocument> findByDocumentName(@Param(value = "documentName") String documentName);
}
