/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.api.interpreter;

/**
 * Exception indicating a problem during policy evaluation
 */
public class PolicyEvaluationException extends Exception {

	private static final long serialVersionUID = 1L;

	/**
	 * Create a new PolicyEvaluationException 
	 */
	public PolicyEvaluationException() {
		super();
	}

	/**
	 * Create a new PolicyEvaluationException 
	 * @param message a message
	 */
	public PolicyEvaluationException(String message) {
		super(message);
	}

	/**
	 * Create a new PolicyEvaluationException 
	 * @param message a message
	 * @param cause causing Throwable
	 */
	public PolicyEvaluationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Create a new PolicyEvaluationException 
	 * @param cause causing Throwable
	 */
	public PolicyEvaluationException(Throwable cause) {
		super(cause);
	}

}
