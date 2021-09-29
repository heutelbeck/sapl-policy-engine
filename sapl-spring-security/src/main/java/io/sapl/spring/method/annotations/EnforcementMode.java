package io.sapl.spring.method.annotations;

/**
 * Reactive policy enforcement mode to be applied in a PEP on a
 * {@link org.reactivestreams.Publisher Publisher}. The mode is selected in the
 * {@link Enforce} annotation. Also see {@link Enforce} for a detailed
 * description of the modes.
 */
public enum EnforcementMode {

	/**
	 * Consume only one decision and enforce it continuously.
	 */
	ONCE,
	/**
	 * Grant access until first deny decision is sent by PEP, update constraint
	 * handlers as indicated in subsequent decisions.
	 */
	UNTIL_DENY,
	/**
	 * Immediately subscribe to original publisher and drop all events until PERMIT
	 * is sent. Update constraint handlers as they come in.
	 */
	FILTER_UNLESS_PERMIT,
}
