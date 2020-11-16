package io.sapl.server.ce.service.pdpconfiguration;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.pdp.PDPConfigurationPublisher;
import io.sapl.server.ce.persistence.VariablesRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for {@link Variable}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariablesService {
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final String DEFAULT_JSON_VALUE = "{\n  \"property\" : \"value\"\n}";

	private final VariablesRepository variableRepository;
	private final PDPConfigurationPublisher pdpConfigurationPublisher;

	/**
	 * Gets all available {@link Variable} instances.
	 * 
	 * @return the available {@link Variable} instances
	 */
	public Collection<Variable> getAll() {
		return this.variableRepository.findAll();
	}

	/**
	 * Gets the amount of available {@link Variable} instances.
	 * 
	 * @return the amount
	 */
	public long getAmount() {
		return this.variableRepository.count();
	}

	/**
	 * Gets a specific {@link Variable} by its id.
	 * 
	 * @param id the id of the {@link Variable}
	 * @return the {@link Variable}
	 */
	public Variable getById(long id) {
		Optional<Variable> optionalEntity = this.variableRepository.findById(id);
		if (!optionalEntity.isPresent()) {
			throw new IllegalArgumentException(String.format("entity with id %d is not existing", id));
		}

		return optionalEntity.get();
	}

	/**
	 * Creates a {@link Variable} with default values.
	 * 
	 * @return the created {@link Variable}
	 * @throws DuplicatedVariableNameException thrown if the name is already used by
	 *                                         another variable
	 */
	public Variable createDefault() throws DuplicatedVariableNameException {
		String name = UUID.randomUUID().toString().replace("-", "");
		String jsonValue = DEFAULT_JSON_VALUE;

		this.checkForDuplicatedName(name, null);

		Variable variable = new Variable(null, name, jsonValue);
		this.variableRepository.save(variable);

		log.info(String.format("created variable %s: %s", name, jsonValue));

		this.publishVariables();

		return variable;
	}

	/**
	 * Creates a {@link Variable}.
	 * 
	 * @param name      the name
	 * @param jsonValue the JSON value
	 * @return the created {@link Variable}
	 * @throws InvalidJsonException            thrown if the provided JSON value is
	 *                                         invalid
	 * @throws DuplicatedVariableNameException thrown if the name is used by another
	 *                                         variable
	 */
	public Variable edit(long id, @NonNull String name, @NonNull String jsonValue)
			throws InvalidJsonException, DuplicatedVariableNameException {
		VariablesService.checkIsJsonValue(jsonValue);
		checkForDuplicatedName(name, id);

		Optional<Variable> optionalEntity = this.variableRepository.findById(id);
		if (!optionalEntity.isPresent()) {
			throw new IllegalArgumentException(String.format("entity with id %d is not existing", id));
		}

		Variable oldVariable = optionalEntity.get();

		Variable editedVariable = new Variable();
		editedVariable.setId(id);
		editedVariable.setName(name);
		editedVariable.setJsonValue(jsonValue);

		this.variableRepository.save(editedVariable);

		log.info(String.format("edited variable: %s -> %s", oldVariable, editedVariable));

		this.publishVariables();

		return oldVariable;
	}

	/**
	 * Deletes a {@link Variable}.
	 * 
	 * @param id the id of the {@link Variable}
	 */
	public void delete(Long id) {
		Optional<Variable> variableToDelete = this.variableRepository.findById(id);

		this.variableRepository.deleteById(id);

		log.info(String.format("deleted variable %s: %s", variableToDelete.get().getName(),
				variableToDelete.get().getJsonValue()));

		this.publishVariables();
	}

	private static void checkIsJsonValue(@NonNull String jsonValue) throws InvalidJsonException {
		if (jsonValue.equals("")) {
			throw new InvalidJsonException(jsonValue);
		}

		try {
			VariablesService.objectMapper.readTree(jsonValue);
		} catch (JsonProcessingException ex) {
			throw new InvalidJsonException(jsonValue);
		}
	}

	/**
	 * Checks a for a duplicated name of a variable.
	 * 
	 * @param name the name to check
	 * @param id   the id of the variable to check
	 * @throws DuplicatedVariableNameException thrown if the name is already used by
	 *                                         another variable
	 */
	private void checkForDuplicatedName(@NonNull String name, Long id) throws DuplicatedVariableNameException {
		Collection<Variable> variablesWithName = this.variableRepository.findByName(name);

		boolean isAnyVariableWithNameExisting = variablesWithName.stream()
				.anyMatch(variable -> variable.getName().equals(name) && variable.getId() != id);

		if (isAnyVariableWithNameExisting) {
			throw new DuplicatedVariableNameException(name);
		}
	}

	private void publishVariables() {
		Collection<Variable> variables = this.getAll();
		this.pdpConfigurationPublisher.publishVariables(variables);
	}
}
