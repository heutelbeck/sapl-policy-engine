package io.sapl.server.ce.service.sapldocument;

import io.sapl.server.ce.model.sapldocument.SaplDocument;

/**
 * Exception thrown if the name of a {@link SaplDocument} to publish is not
 * unique.
 */
public class PublishedDocumentNameCollisionException extends Exception {
	/**
	 * Creates a new instance of the
	 * {@link PublishedDocumentNameCollisionException} class.
	 * 
	 * @param publishedSaplDocumentId the id of the {@link SaplDocument} with the
	 *                                conflicting name
	 * @param publishedVersion        the version of the {@link SaplDocument} with
	 *                                the conflicting name
	 */
	public PublishedDocumentNameCollisionException(long publishedSaplDocumentId, int publishedVersion) {
		super(String.format("Version %d of SAPL Document with identifier %d is already published with identical name.",
				publishedVersion, publishedSaplDocumentId));
	}
}
