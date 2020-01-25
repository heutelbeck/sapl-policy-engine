/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "io.sapl")
public class SAPLProperties {

	public enum PDPType {

		EMBEDDED, REMOTE

	}

	public enum PDPConfigType {

		RESOURCES, FILESYSTEM

	}

	public enum PRPType {

		RESOURCES, FILESYSTEM

	}

	public enum PRPIndexType {

		SIMPLE, FAST

	}

	private PDPType pdpType = PDPType.EMBEDDED;

	private PDPConfigType pdpConfigType = PDPConfigType.RESOURCES;

	private PRPType prpType = PRPType.RESOURCES;

	private PRPIndexType index = PRPIndexType.SIMPLE;

	private Resources resources = new Resources();

	private Filesystem filesystem = new Filesystem();

	private Remote remote = new Remote();

	private boolean policyEnforcementFilter;

	@Data
	public static class Filesystem {

		private String configPath = "~/policies";

		private String policiesPath = "~/policies";

	}

	@Data
	public static class Resources {

		private String configPath = "/policies";

		private String policiesPath = "/policies";

	}

	@Data
	public static class Remote {

		private static final int DEFAULT_REMOTE_PORT = 8443;

		private boolean active;

		private String host = "localhost";

		private int port = DEFAULT_REMOTE_PORT;

		private String key;

		private String secret;

	}

}
