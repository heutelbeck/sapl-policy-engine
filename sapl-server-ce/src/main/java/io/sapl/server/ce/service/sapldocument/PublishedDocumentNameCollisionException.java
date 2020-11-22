/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
