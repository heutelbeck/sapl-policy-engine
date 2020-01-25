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
package io.sapl.api.pdp;

/**
 * Indicates an error during PDP configuration
 * */
public class PDPConfigurationException extends Exception {

	private static final long serialVersionUID = -6103041704294876849L;

	/**
	 * Create a new PDPConfigurationException
	 */
	public PDPConfigurationException() {
		super();
	}

	/**
	 * Create a new PDPConfigurationException
	 * @param message a message
	 */
	public PDPConfigurationException(String message) {
		super(message);
	}

	/**
	 * Create a new PDPConfigurationException
	 * @param message a message
	 * @param cause causing Throwable
	 */
	public PDPConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Create a new PDPConfigurationException
	 * @param cause causing Throwable
	 */
	public PDPConfigurationException(Throwable cause) {
		super(cause);
	}

}
