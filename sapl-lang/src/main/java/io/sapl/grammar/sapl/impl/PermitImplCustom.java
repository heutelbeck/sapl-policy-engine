package io.sapl.grammar.sapl.impl;

import io.sapl.api.pdp.Decision;

public class PermitImplCustom extends PermitImpl {

	@Override
	public Decision getDecision() {
		return Decision.PERMIT;
	}

}
