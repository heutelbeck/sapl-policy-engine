package io.sapl.server.ce.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

/**
 * Model for a set of credentials of a single client.
 */
@Data
@Table(name = "ClientCredentials")
@Entity
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
	private String key;

	/**
	 * The hashed secret (password).
	 */
	private String hashedSecret;
}
