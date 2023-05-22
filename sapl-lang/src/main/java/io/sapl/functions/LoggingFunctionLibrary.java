/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FunctionLibrary(name = LoggingFunctionLibrary.NAME, description = LoggingFunctionLibrary.DESCRIPTION)
public class LoggingFunctionLibrary {

	private static final String TEMPLATE = "[SAPL] {} {}";

	public static final String NAME = "log";

	public static final String DESCRIPTION = "Utility functions for dumping data from policy evaluation on the PDP console for debugging of policies.";

	private static final String DEBUG_SPY_DOC = "log.debugSpy(MESSAGE, VALUE): logs the value prepended with the message on the console at log level DEBUG. Function acts as identity form the perspective of the PDP. This can be used to wrap any value in a SAPL expression without changing the overall structure of the policy.";

	private static final String INFO_SPY_DOC = "log.infoSpy(MESSAGE, VALUE): logs the value prepended with the message on the console at log level INFO. Function acts as identity form the perspective of the PDP. This can be used to wrap any value in a SAPL expression without changing the overall structure of the policy.";

	private static final String ERROR_SPY_DOC = "log.errorSpy(MESSAGE, VALUE): logs the value prepended with the message on the console at log level ERROR. Function acts as identity form the perspective of the PDP. This can be used to wrap any value in a SAPL expression without changing the overall structure of the policy.";

	private static final String TRACE_SPY_DOC = "log.traceSpy(MESSAGE, VALUE): logs the value prepended with the message on the console at log level TRACE. Function acts as identity form the perspective of the PDP. This can be used to wrap any value in a SAPL expression without changing the overall structure of the policy.";

	private static final String WARN_SPY_DOC = "log.warnSpy(MESSAGE, VALUE): logs the value prepended with the message on the console at log level WARN. Function acts as identity form the perspective of the PDP. This can be used to wrap any value in a SAPL expression without changing the overall structure of the policy.";

	private static final String DEBUG_DOC = "log.debug(MESSAGE, VALUE): logs the value prepended with the message on the console at log level DEBUG. Always returns a true value. This function is useful to add an additional line in a where block of a policy. As the function return true, the rest of the policy evaluation is not affected.";

	private static final String INFO_DOC = "log.info(MESSAGE, VALUE): logs the value prepended with the message on the console at log level INFO. Always returns a true value. This function is useful to add an additional line in a where block of a policy. As the function return true, the rest of the policy evaluation is not affected.";

	private static final String ERROR_DOC = "log.error(MESSAGE, VALUE): logs the value prepended with the message on the console at log level ERROR. Always returns a true value. This function is useful to add an additional line in a where block of a policy. As the function return true, the rest of the policy evaluation is not affected.";

	private static final String TRACE_DOC = "log.trace(MESSAGE, VALUE): logs the value prepended with the message on the console at log level TRACE. Always returns a true value. This function is useful to add an additional line in a where block of a policy. As the function return true, the rest of the policy evaluation is not affected.";

	private static final String WARN_DOC = "log.warn(MESSAGE, VALUE): logs the value prepended with the message on the console at log level WARN. Always returns a true value. This function is useful to add an additional line in a where block of a policy. As the function return true, the rest of the policy evaluation is not affected.";

	@Function(docs = INFO_SPY_DOC)
	public static Val infoSpy(@Text Val message, Val value) {
		log.info(TEMPLATE, message.getText(), val2String(value));
		return value;
	}

	@Function(docs = ERROR_SPY_DOC)
	public static Val errorSpy(@Text Val message, Val value) {
		log.error(TEMPLATE, message.getText(), val2String(value));
		return value;
	}

	@Function(docs = TRACE_SPY_DOC)
	public static Val traceSpy(@Text Val message, Val value) {
		log.trace(TEMPLATE, message.getText(), val2String(value));
		return value;
	}

	@Function(docs = WARN_SPY_DOC)
	public static Val warnSpy(@Text Val message, Val value) {
		log.warn(TEMPLATE, message.getText(), val2String(value));
		return value;
	}

	@Function(docs = DEBUG_SPY_DOC)
	public static Val debugSpy(@Text Val message, Val value) {
		log.debug(TEMPLATE, message.getText(), val2String(value));
		return value;
	}

	@Function(docs = INFO_DOC)
	public static Val info(@Text Val message, Val value) {
		log.info(TEMPLATE, message.getText(), val2String(value));
		return Val.TRUE;
	}

	@Function(docs = ERROR_DOC)
	public static Val error(@Text Val message, Val value) {
		log.error(TEMPLATE, message.getText(), val2String(value));
		return Val.TRUE;
	}

	@Function(docs = TRACE_DOC)
	public static Val trace(@Text Val message, Val value) {
		log.trace(TEMPLATE, message.getText(), val2String(value));
		return Val.TRUE;
	}

	@Function(docs = WARN_DOC)
	public static Val warn(@Text Val message, Val value) {
		log.warn(TEMPLATE, message.getText(), val2String(value));
		return Val.TRUE;
	}

	@Function(docs = DEBUG_DOC)
	public static Val debug(@Text Val message, Val value) {
		log.debug(TEMPLATE, message.getText(), val2String(value));
		return Val.TRUE;
	}

	private static String val2String(Val value) {
		if (value.isError())
			return "ERROR: " + value.getMessage();
		return value.isDefined() ? value.get().toString() : "undefined";
	}

}
