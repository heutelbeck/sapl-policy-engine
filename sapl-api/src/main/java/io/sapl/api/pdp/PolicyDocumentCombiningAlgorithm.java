package io.sapl.api.pdp;

/**
 * Enumeration of the algorithms supported by the SAPL policy engine
 * to combine SAPL documents (holding a policy set or a policy).
 */
public enum PolicyDocumentCombiningAlgorithm {

	DENY_OVERRIDES,
	PERMIT_OVERRIDES,
	ONLY_ONE_APPLICABLE,
	DENY_UNLESS_PERMIT,
	PERMIT_UNLESS_DENY

}
