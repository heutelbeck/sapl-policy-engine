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
package io.sapl.api.pip;

public class AttributeException extends Exception {

	private static final long serialVersionUID = 1L;

	public AttributeException() {
		super();
	}

	public AttributeException(String message) {
		super(message);
	}

	/**
	 * Create a new PolicyEvaluationException
	 * 
	 * @param format format string
	 * @param args   arguments for format string
	 */
	public AttributeException(String format, Object... args) {
		super(String.format(format, args));
	}

	/**
	 * Create a new PolicyEvaluationException
	 * 
	 * @param format format string
	 * @param cause  causing Throwable
	 * @param args   arguments for format string
	 */
	public AttributeException(Throwable cause, String format, Object... args) {
		super(String.format(format, args), cause);
	}

	public AttributeException(String message, Throwable cause) {
		super(message, cause);
	}

	public AttributeException(Throwable cause) {
		super(cause);
	}

}
