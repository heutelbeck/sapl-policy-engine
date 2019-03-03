/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class ExpressionImplCustom extends io.sapl.grammar.sapl.impl.ExpressionImpl {

	protected static final String UNDEFINED = "undefined";
	protected static final String ARITHMETIC_OPERATION_TYPE_MISMATCH = "Type mismatch. Arithmetic operation expects number values, but got: '%s'.";
	protected static final String BOOLEAN_OPERATION_TYPE_MISMATCH = "Type mismatch. Boolean operation expects boolean values, but got: '%s'.";
	protected static final String TEXT_OPERATION_TYPE_MISMATCH = "Type mismatch. Text operation expects text values, but got: '%s'.";
	protected static final String ARRAY_OPERATION_TYPE_MISMATCH = "Type mismatch. Array operation expects Array values, but got: '%s'.";
	protected static final String OBJECT_OPERATION_TYPE_MISMATCH = "Type mismatch. Object operation expects Object values, but got: '%s'.";
	protected static final String UNDEFINED_MISMATCH = "Type mismatch. Defined parameters expected, but got 'undefined'.";

	protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;

}
