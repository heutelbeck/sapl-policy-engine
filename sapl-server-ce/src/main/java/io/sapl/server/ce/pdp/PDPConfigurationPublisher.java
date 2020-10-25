package io.sapl.server.ce.pdp;

import java.util.Collection;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import lombok.NonNull;

/**
 * Publisher for changed configuration of the PDP.
 */
public interface PDPConfigurationPublisher {
	/**
	 * Publishes a changed {@link PolicyDocumentCombiningAlgorithm}.
	 * 
	 * @param algorithm the changed {@link PolicyDocumentCombiningAlgorithm}
	 */
	void publishCombiningAlgorithm(@NonNull PolicyDocumentCombiningAlgorithm algorithm);

	/**
	 * Publishes a changed collection of {@link Variable} instances.
	 * 
	 * @param variables the collection of {@link Variable} instances
	 */
	void publishVariables(@NonNull Collection<Variable> variables);
}
