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
package io.sapl.prp.resources;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.InitializationException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pip.AttributeException;
import io.sapl.pdp.PolicyDecisionPointFactory;

public class ResourcesPolicyRetrievalPointTest {

	@Test
	public void loadPolicies() throws IOException, URISyntaxException, FunctionException, AttributeException,
			PolicyEvaluationException, InitializationException {
		PolicyDecisionPointFactory.resourcesPolicyDecisionPoint();
	}

}
