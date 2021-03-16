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
package io.sapl.interpreter.combinators;

import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import io.sapl.grammar.sapl.impl.DenyOverridesCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.DenyUnlessPermitCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.OnlyOneApplicableCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.PermitOverridesCombiningAlgorithmImplCustom;
import io.sapl.grammar.sapl.impl.PermitUnlessDenyCombiningAlgorithmImplCustom;

public class DocumentsCombinatorFactoryTest {

	@Test
	public void permitUnlessDeny() {
		assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(PERMIT_UNLESS_DENY))
				.isInstanceOf(PermitUnlessDenyCombiningAlgorithmImplCustom.class);
	}

	@Test
	public void permitOverrides() {
		assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(PERMIT_OVERRIDES))
				.isInstanceOf(PermitOverridesCombiningAlgorithmImplCustom.class);
	}

	@Test
	public void denyOverrides() {
		assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(DENY_OVERRIDES))
				.isInstanceOf(DenyOverridesCombiningAlgorithmImplCustom.class);
	}

	@Test
	public void oneApplicable() {
		assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(ONLY_ONE_APPLICABLE))
				.isInstanceOf(OnlyOneApplicableCombiningAlgorithmImplCustom.class);
	}

	@Test
	public void dynyUnlessPErmit() {
		assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(DENY_UNLESS_PERMIT))
				.isInstanceOf(DenyUnlessPermitCombiningAlgorithmImplCustom.class);
	}
}
