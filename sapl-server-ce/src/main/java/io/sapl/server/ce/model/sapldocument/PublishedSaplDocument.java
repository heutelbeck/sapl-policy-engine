package io.sapl.server.ce.model.sapldocument;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * A published SAPL document.
 */
@Data
@Table(name = "PublishedSaplDocument")
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PublishedSaplDocument {
	/**
	 * The unique identifier of the SAPL document.
	 */
	@Id
	@GeneratedValue
	@Column(name = "Id", nullable = false)
	private Long id;

	/**
	 * The value / text of the published SAPL document.
	 */
	@Column(length = 2000)
	private String value;

	/**
	 * The name included in the value / text of the SAPL document version
	 * (redundancy for better query performance).
	 */
	@Column(length = 250, unique = true)
	private String name;

	/**
	 * Imports a {@link SaplDocumentVersion} to the entity.
	 * 
	 * @param saplDocumentVersion the {@link SaplDocumentVersion} to import
	 */
	public void importSaplDocumentVersion(@NonNull SaplDocumentVersion saplDocumentVersion) {
		this.setValue(saplDocumentVersion.getValue());
		this.setName(saplDocumentVersion.getName());
	}
}
