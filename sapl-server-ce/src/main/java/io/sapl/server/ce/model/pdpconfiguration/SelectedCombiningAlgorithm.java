package io.sapl.server.ce.model.pdpconfiguration;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * The selected combining algorithm.
 */
@Data
@Table(name = "SelectedCombiningAlgorithm")
@Entity
@NoArgsConstructor
public class SelectedCombiningAlgorithm {
	public SelectedCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm selection) {
		this.selection = selection;
	}

	/**
	 * The unique identifier of the SAPL document.
	 */
	@Id
	@GeneratedValue
	@Column(name = "Id", nullable = false)
	private Long id;

	/**
	 * The selection.
	 */
	@Column
	private PolicyDocumentCombiningAlgorithm selection;
}
