package io.sapl.server.ce.persistence;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.sapl.server.ce.model.sapldocument.SaplDocumentVersion;

/**
 * Interface for a repository for accessing persisted
 * {@link SaplDocumentVersion}.
 */
@Repository
public interface SaplDocumentsVersionRepository extends CrudRepository<SaplDocumentVersion, Long> {
}
