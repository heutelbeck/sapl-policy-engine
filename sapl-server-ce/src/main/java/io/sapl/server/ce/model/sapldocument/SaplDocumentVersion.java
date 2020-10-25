package io.sapl.server.ce.model.sapldocument;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Entity for a version of a SAPL document.
 */
@Data
@Table(name = "SaplDocumentVersion")
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class SaplDocumentVersion {
	/**
	 * The unique identifier of the SAPL document version.
	 */
	@Id
	@GeneratedValue
	@Column(name = "Id", nullable = false)
	private Long id;

	/**
	 * The {@link SaplDocument} this version is belonging to.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "saplDocument_fk")
	@ToString.Exclude
	@EqualsAndHashCode.Exclude
	private SaplDocument saplDocument;

	/**
	 * The version number.
	 */
	@Column
	private int versionNumber;

	/**
	 * The value / text of the SAPL document version.
	 */
	@Column(length = 50000)
	private String value;

	/**
	 * The name included in the value / text of the SAPL document version
	 * (redundancy for better query performance).
	 */
	@Column(length = 50000)
	private String name;
}
