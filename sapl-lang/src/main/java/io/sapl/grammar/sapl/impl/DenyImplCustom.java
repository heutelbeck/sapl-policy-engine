package io.sapl.grammar.sapl.impl;

import io.sapl.api.pdp.Decision;

public class DenyImplCustom extends DenyImpl {

	@Override
	public Decision getDecision() {
		return Decision.DENY;
	}

}
