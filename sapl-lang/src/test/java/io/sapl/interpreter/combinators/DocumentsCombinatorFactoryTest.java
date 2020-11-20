package io.sapl.interpreter.combinators;

import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
import static io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DocumentsCombinatorFactoryTest {

	@Test
	public void permitUnlessDeny() {
		assertThat(DocumentsCombinatorFactory.getCombinator(PERMIT_UNLESS_DENY))
				.isInstanceOf(PermitUnlessDenyCombinator.class);
	}

	@Test
	public void permitOverrides() {
		assertThat(DocumentsCombinatorFactory.getCombinator(PERMIT_OVERRIDES))
				.isInstanceOf(PermitOverridesCombinator.class);
	}

	@Test
	public void denyOverrides() {
		assertThat(DocumentsCombinatorFactory.getCombinator(DENY_OVERRIDES))
				.isInstanceOf(DenyOverridesCombinator.class);
	}

	@Test
	public void oneApplicable() {
		assertThat(DocumentsCombinatorFactory.getCombinator(ONLY_ONE_APPLICABLE))
				.isInstanceOf(OnlyOneApplicableCombinator.class);
	}

	@Test
	public void dynyUnlessPErmit() {
		assertThat(DocumentsCombinatorFactory.getCombinator(DENY_UNLESS_PERMIT))
				.isInstanceOf(DenyUnlessPermitCombinator.class);
	}
}
