package io.sapl.server.ce.service.pdpconfiguration;

import java.util.Collection;

import org.springframework.stereotype.Service;

import com.google.common.collect.Iterables;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.model.pdpconfiguration.SelectedCombiningAlgorithm;
import io.sapl.server.ce.pdp.PDPConfigurationPublisher;
import io.sapl.server.ce.persistence.SelectedCombiningAlgorithmRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Service for managing the combining algorithm.
 */
@Service
@RequiredArgsConstructor
public class CombiningAlgorithmService {
	private static final PolicyDocumentCombiningAlgorithm DEFAULT = PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;

	private final SelectedCombiningAlgorithmRepository selectedCombiningAlgorithmRepository;
	private final PDPConfigurationPublisher pdpConfigurationPublisher;

	/**
	 * Gets the selected combining algorithm.
	 * 
	 * @return the selected combining algorithm
	 */
	public PolicyDocumentCombiningAlgorithm getSelected() {
		Collection<SelectedCombiningAlgorithm> entities = this.selectedCombiningAlgorithmRepository.findAll();
		if (entities.isEmpty()) {
			this.selectedCombiningAlgorithmRepository
					.save(new SelectedCombiningAlgorithm(CombiningAlgorithmService.DEFAULT));
			return CombiningAlgorithmService.DEFAULT;
		}

		SelectedCombiningAlgorithm relevantEntity = Iterables.get(entities, 0);
		return relevantEntity.getSelection();
	}

	/**
	 * Gets the available / selectable combining algorithms.
	 * 
	 * @return the algorithm types
	 */
	public PolicyDocumentCombiningAlgorithm[] getAvailable() {
		return PolicyDocumentCombiningAlgorithm.values();
	}

	/**
	 * Sets the combining algorithm.
	 * 
	 * @param algorithmType the combining algorithm to set
	 */
	public void setSelected(@NonNull PolicyDocumentCombiningAlgorithm algorithmType) {
		this.selectedCombiningAlgorithmRepository.deleteAll();
		this.selectedCombiningAlgorithmRepository.save(new SelectedCombiningAlgorithm(algorithmType));

		this.pdpConfigurationPublisher.publishCombiningAlgorithm(algorithmType);
	}
}
