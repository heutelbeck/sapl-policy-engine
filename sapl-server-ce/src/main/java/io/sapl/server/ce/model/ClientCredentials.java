package io.sapl.server.ce.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for a set of credentials of a single client.
 */
@Data
@Table(name = "ClientCredentials")
@Entity
@NoArgsConstructor
public class ClientCredentials {
	/**
	 * The unique identifier.
	 */
	@Id
	@GeneratedValue
	@Column(name = "Id", nullable = false)
	private Long id;

	/**
	 * The key (user)
	 */
	@Column(length = 250, name = "clientKey") // MariaDB / MySQL do not like a column with the name "key"
	private String key;

	/**
	 * The hashed secret (password).
	 */
	@Column(length = 250, name = "clientSecret")
	private String secret;
}
