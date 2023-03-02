/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.lt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;
import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "io.sapl.server-lt")
public class SAPLServerLTProperties {

	// authentication methods
	private boolean allowNoAuth = false;
	private boolean allowBasicAuth = true;
	private boolean allowApiKeyAuth = false;
	private boolean allowOauth2Auth = false;

	// Basic authentication
	private String key = "";
	private String secret = "";

	// API Key authentication
	private String apiKeyHeaderName = "API_KEY";
	private List<String> allowedApiKeys = List.of();
}
