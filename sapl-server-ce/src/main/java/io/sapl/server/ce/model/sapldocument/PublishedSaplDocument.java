package io.sapl.server.ce.model.sapldocument;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Table(name = "PublishedSaplDocument")
public class PublishedSaplDocument {
	@Id
	@Column(name = "saplDocumentId", nullable = false)
	private Long saplDocumentId;

	@Column(name = "version", nullable = false)
	private Integer version;

	@Column(length = 250, unique = true, nullable = false)
	private String documentName;

	@Column(length = SaplDocumentVersion.MAX_DOCUMENT_SIZE, nullable = false)
	private String document;

	public void importSaplDocumentVersion(@NonNull SaplDocumentVersion saplDocumentVersion) {
		setSaplDocumentId(saplDocumentVersion.getSaplDocument().getId());
		setVersion(saplDocumentVersion.getVersionNumber());
		setDocumentName(saplDocumentVersion.getName());
		setDocument(saplDocumentVersion.getValue());
	}
}
