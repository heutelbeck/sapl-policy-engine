package io.sapl.api.pdp;

/**
 * Certain PDP or PRP implementations need to clean up resources (e.g. subscriptions,
 * threads) when they are no longer needed. By implementing this interface, they show this
 * fact to their clients.
 */
public interface Disposable {

	/**
	 * When clients of a policy decision point or policy retrieval point implementing the
	 * {@link Disposable} interface no longer need it, they should call this method to
	 * give them the chance to clean up resources.
	 */
	void dispose();

}
