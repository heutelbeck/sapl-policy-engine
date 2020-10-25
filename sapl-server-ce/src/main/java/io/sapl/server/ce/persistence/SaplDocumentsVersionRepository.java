package io.sapl.server.ce.persistence;

import org.springframework.data.repository.CrudRepository;

import io.sapl.server.ce.model.sapldocument.SaplDocumentVersion;

/**
 * Interface for a repository for accessing persisted
 * {@link SaplDocumentVersion}.
 */
public interface SaplDocumentsVersionRepository extends CrudRepository<SaplDocumentVersion, Long> {
}
