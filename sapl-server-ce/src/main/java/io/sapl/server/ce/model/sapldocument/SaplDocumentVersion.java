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
	@Column(length = 2000)
	private String value;

	/**
	 * The name included in the value / text of the SAPL document version
	 * (redundancy for better query performance).
	 */
	@Column(length = 250)
	private String name;
}
