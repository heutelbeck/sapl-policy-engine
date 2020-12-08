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
package io.sapl.server.ce.service.pdpconfiguration;

import java.util.Collection;

import javax.annotation.PostConstruct;

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

	@PostConstruct
	public void init() {
		pdpConfigurationPublisher.publishCombiningAlgorithm(getSelected());
	}

	/**
	 * Gets the selected combining algorithm.
	 * 
	 * @return the selected combining algorithm
	 */
	public PolicyDocumentCombiningAlgorithm getSelected() {
		Collection<SelectedCombiningAlgorithm> entities = selectedCombiningAlgorithmRepository.findAll();
		if (entities.isEmpty()) {
			selectedCombiningAlgorithmRepository
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
		selectedCombiningAlgorithmRepository.deleteAll();
		selectedCombiningAlgorithmRepository.save(new SelectedCombiningAlgorithm(algorithmType));
		pdpConfigurationPublisher.publishCombiningAlgorithm(algorithmType);
	}
}
