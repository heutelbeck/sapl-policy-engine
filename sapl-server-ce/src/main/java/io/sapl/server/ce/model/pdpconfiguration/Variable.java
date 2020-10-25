package io.sapl.server.ce.model.pdpconfiguration;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * A SAPL document.
 */
@Data
@Table(name = "Variable")
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class Variable {
	/**
	 * The unique identifier of the SAPL document.
	 */
	@Id
	@GeneratedValue
	@Column(name = "Id", nullable = false)
	private Long id;

	/**
	 * The name of the variable.
	 */
	@Column(name = "name")
	private String name;

	/**
	 * The JSON encoded value of the variable.
	 */
	@Column(name = "json")
	private String jsonValue;
}
