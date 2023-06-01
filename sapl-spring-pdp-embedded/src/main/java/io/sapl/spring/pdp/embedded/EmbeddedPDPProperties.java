/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pdp.embedded;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Configuration properties for the embedded PDP.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "io.sapl.pdp.embedded")
public class EmbeddedPDPProperties {

	/**
	 * Selects the source of configuration and policies:
	 * <p>
	 * The options are:
	 * <p>
	 * - RESOURCES : Loads a fixed set of documents and pdp.json from the bundled
	 * resource. These will be loaded once and cannot be updated at runtime of the
	 * system.
	 * <p>
	 * - FILESYSTEM: Monitors directories for documents and configuration. Will
	 * automatically update any changes made to the documents and configuration at
	 * runtime. Changes will directly be reflected in the decisions made in already
	 * existing subscriptions and send new decisions if applicable.
	 */
	@NotNull
	private PDPDataSource pdpConfigType = PDPDataSource.RESOURCES;

	/**
	 * Selects the indexing algorithm used by the PDP.
	 * <p>
	 * The options are:
	 * <p>
	 * - NAIVE : A simple implementation for systems with small numbers of
	 * documents.
	 * <p>
	 * - CANONICAL : An improved index for systems with large numbers of documents.
	 * Takes more time to update and initialize but significantly reduces retrieval
	 * time.
	 */
	@NotNull
	private IndexType index = IndexType.NAIVE;

	/**
	 * This property sets the path to the folder where the pdp.json configuration
	 * file is located.
	 * <p>
	 * If the pdpConfigType is set to RESOURCES, / is the root of the context path.
	 * For FILESYSTEM, it must be a valid path on the system's filesystem.
	 */
	@NotEmpty
	private String configPath = "/policies";

	/**
	 * This property sets the path to the folder where the *.sapl documents are
	 * located.
	 * <p>
	 * If the pdpConfigType is set to RESOURCES, / is the root of the context path.
	 * For FILESYSTEM, it must be a valid path on the system's file system.
	 */
	@NotEmpty
	private String policiesPath = "/policies";

	/**
	 * Indicate whether to load policies from the resources or the file system.
	 */
	public enum PDPDataSource {

		/**
		 * Indicates to load static policies from the resources bundled in the JAR.
		 */
		RESOURCES,
		/**
		 * Indicates to load policies dynamically from a monitored folder on the file
		 * system.
		 */
		FILESYSTEM

	}

	/**
	 * Selects the indexing algorithm.
	 */
	public enum IndexType {

		/**
		 * Simple default index. 
		 */
		NAIVE,
		/**
		 * High-performance policy index for large collections of policies.
		 */
		CANONICAL

	}

	/**
	 * If this property is set to true, JSON in logged traces and reports is pretty
	 * printed.
	 */
	private boolean prettyPrintReports = false;

	/**
	 * If this property is set to true, the full JSON evaluation trace is logged on
	 * each decision.
	 */
	private boolean printTrace = false;

	/**
	 * If this property is set to true, the JSON evaluation report is logged on each
	 * decision.
	 */
	private boolean printJsonReport = false;

	/**
	 * If this property is set to true, the textual decision report is logged on
	 * each decision.
	 */
	private boolean printTextReport = false;

}
