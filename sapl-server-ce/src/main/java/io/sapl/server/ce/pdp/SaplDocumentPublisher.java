package io.sapl.server.ce.pdp;

import io.sapl.server.ce.model.sapldocument.SaplDocument;
import lombok.NonNull;

/**
 * Publisher for changed {@link SaplDocument} instances.
 */
public interface SaplDocumentPublisher {
	/**
	 * Publishes an {@link SaplDocument}.
	 * 
	 * @param saplDocument the publishes {@link SaplDocument
	 */
	void publishSaplDocument(@NonNull SaplDocument saplDocument);

	/**
	 * Unpublishes an {@link SaplDocument}.
	 * 
	 * @param saplDocument the unpublished {@link SaplDocument
	 */
	void unpublishSaplDocument(@NonNull SaplDocument saplDocument);
}
