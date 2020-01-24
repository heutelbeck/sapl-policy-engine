/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.pdp.embedded.config;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import reactor.core.publisher.Flux;

public interface PDPConfigurationProvider {

	Flux<DocumentsCombinator> getDocumentsCombinator();

	Flux<Map<String, JsonNode>> getVariables();

	default DocumentsCombinator convert(PolicyDocumentCombiningAlgorithm algorithm) {
		switch (algorithm) {
		case PERMIT_UNLESS_DENY:
			return new PermitUnlessDenyCombinator();
		case DENY_UNLESS_PERMIT:
			return new DenyUnlessPermitCombinator();
		case PERMIT_OVERRIDES:
			return new PermitOverridesCombinator();
		case DENY_OVERRIDES:
			return new DenyOverridesCombinator();
		case ONLY_ONE_APPLICABLE:
			return new OnlyOneApplicableCombinator();
		default:
			throw new IllegalArgumentException("Algorithm FIRST_APPLICABLE is not allowed for PDP level combination.");
		}
	}

}
