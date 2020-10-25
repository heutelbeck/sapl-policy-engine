package io.sapl.server.ce.service.documentation;

import static io.sapl.server.ce.utils.StreamUtils.distinctByKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.spring.pdp.embedded.PolicyInformationPointsDocumentation;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for reading {@link PolicyInformationPoint} instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipService {
	private final PolicyInformationPointsDocumentation policyInformationPointsDocumentation;

	/**
	 * Gets all available {@link PolicyInformationPoint}s.
	 * 
	 * @return the instances
	 */
	public Collection<PolicyInformationPoint> getAll() {
		Collection<PolicyInformationPointDocumentation> policyInformationPointDocumentations = this
				.getPolicyInformationPointDocumentations();

		return policyInformationPointDocumentations.stream()
				.map((PolicyInformationPointDocumentation policyInformationPointDocumentation) -> PipService
						.toPolicyInformationPoint(policyInformationPointDocumentation))
				.collect(Collectors.toList());
	}

	/**
	 * Gets the amount of available {@link PolicyInformationPoint}s.
	 * 
	 * @return the amount
	 */
	public long getAmount() {
		return this.getPolicyInformationPointDocumentations().size();
	}

	/**
	 * Gets a single PIP by its name.
	 * 
	 * @param pipName the name of the PIP
	 * @return the PIP
	 */
	public PolicyInformationPoint getByName(@NonNull String pipName) {
		Optional<PolicyInformationPoint> policyInformationPointAsOptional = this
				.getPolicyInformationPointDocumentations().stream()
				.filter((
						PolicyInformationPointDocumentation policyInformationPointDocumentation) -> policyInformationPointDocumentation
								.getName().equals(pipName))
				.map((PolicyInformationPointDocumentation policyInformationPointDocumentation) -> PipService
						.toPolicyInformationPoint(policyInformationPointDocumentation))
				.findFirst();
		if (!policyInformationPointAsOptional.isPresent()) {
			throw new IllegalStateException(String.format("PIP with name %s is not available", pipName));
		}

		return policyInformationPointAsOptional.get();
	}

	/**
	 * Gets the functions of a specific PIP.
	 * 
	 * @param pipName the name of the PIP
	 * @return the functions
	 */
	public Collection<Function> getFunctionsOfPIP(@NonNull String pipName) {
		PolicyInformationPoint policyInformationPoint = this.getByName(pipName);
		Map<String, String> functionDocumentation = policyInformationPoint.getFunctionDocumentation();

		List<Function> functions = new ArrayList<Function>(functionDocumentation.size());
		for (String name : functionDocumentation.keySet()) {
			String documentation = functionDocumentation.get(name);

			Function function = new Function().setName(name).setDocumentation(documentation);
			functions.add(function);
		}

		return functions;
	}

	private static PolicyInformationPoint toPolicyInformationPoint(
			@NonNull PolicyInformationPointDocumentation policyInformationPointDocumentation) {
		return new PolicyInformationPoint().setName(policyInformationPointDocumentation.getName())
				.setDescription(policyInformationPointDocumentation.getDescription())
				.setFunctionDocumentation(policyInformationPointDocumentation.getDocumentation());
	}

	private Collection<PolicyInformationPointDocumentation> getPolicyInformationPointDocumentations() {
		if (this.policyInformationPointsDocumentation == null) {
			log.warn("cannot get documentation for PIPs (configured bean)");
			return Collections.emptyList();
		}

		return this.policyInformationPointsDocumentation.getDocumentation().stream()
				.filter(distinctByKey(PolicyInformationPointDocumentation::getName)).collect(Collectors.toList());
	}
}
