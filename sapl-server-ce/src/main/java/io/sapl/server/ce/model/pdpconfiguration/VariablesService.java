/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.server.ce.model.pdpconfiguration;

import java.util.Collection;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.pdp.PDPConfigurationPublisher;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class VariablesService {
    public static final int    MIN_NAME_LENGTH    = 1;
    public static final int    MAX_NAME_LENGTH    = 100;
    public static final String DEFAULT_JSON_VALUE = "{}";

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

    private final VariablesRepository       variableRepository;
    private final PDPConfigurationPublisher pdpConfigurationPublisher;

    @PostConstruct
    public void init() {
        pdpConfigurationPublisher.publishVariables(getAll());
    }

    public Collection<Variable> getAll() {
        return variableRepository.findAll();
    }

    public long getAmount() {
        return variableRepository.count();
    }

    public Optional<Variable> getById(long id) {
        return variableRepository.findById(id);
    }

    public Variable create(@NonNull String name) throws InvalidVariableNameException, DuplicatedVariableNameException {
        String jsonValue = DEFAULT_JSON_VALUE;

        checkForInvalidName(name);
        checkForDuplicatedName(name, null);

        Variable variable = new Variable(null, name, jsonValue);
        variableRepository.save(variable);

        log.info("created variable {}: {}", name, jsonValue);

        publishVariables();

        return variable;
    }

    private void checkForInvalidName(String name) throws InvalidVariableNameException {
        int nameLength = name.length();
        if (nameLength < MIN_NAME_LENGTH || nameLength > MAX_NAME_LENGTH) {
            throw new InvalidVariableNameException(name);
        }
    }

    public Variable edit(long id, @NonNull String name, @NonNull String jsonValue)
            throws InvalidJsonException, InvalidVariableNameException, DuplicatedVariableNameException {
        VariablesService.checkIsJsonValue(jsonValue);
        checkForInvalidName(name);
        checkForDuplicatedName(name, id);

        Variable oldVariable = getExistingById(id);

        Variable editedVariable = new Variable();
        editedVariable.setId(id);
        editedVariable.setName(name);
        editedVariable.setJsonValue(jsonValue);

        variableRepository.save(editedVariable);

        log.info("edited variable: {} -> {}", oldVariable, editedVariable);

        publishVariables();

        return editedVariable;
    }

    public void delete(@NonNull Long id) {
        Optional<Variable> variableToDelete = variableRepository.findById(id);
        if (variableToDelete.isEmpty()) {
            throw new IllegalArgumentException(String.format("variable with id %d is not existing", id));
        }

        variableRepository.deleteById(id);
        log.info("deleted variable {}: {}", variableToDelete.get().getName(), variableToDelete.get().getJsonValue());
        publishVariables();
    }

    private static void checkIsJsonValue(String jsonValue) throws InvalidJsonException {
        if (jsonValue.isBlank()) {
            throw new InvalidJsonException(jsonValue);
        }

        try {
            VariablesService.objectMapper.readTree(jsonValue);
        } catch (JsonProcessingException ex) {
            throw new InvalidJsonException(jsonValue, ex);
        }
    }

    private Variable getExistingById(long id) {
        Optional<Variable> optionalVariable = getById(id);
        if (optionalVariable.isEmpty()) {
            throw new IllegalArgumentException(String.format("variable with id %d is not existing", id));
        }

        return optionalVariable.get();
    }

    /**
     * Checks a for a duplicated name of a variable.
     *
     * @param name the name to check
     * @param id   the id of the variable to check
     * @throws DuplicatedVariableNameException thrown if the name is already used by
     *                                         another variable
     */
    private void checkForDuplicatedName(String name, Long id) throws DuplicatedVariableNameException {
        Collection<Variable> variablesWithName = variableRepository.findByName(name);

        boolean isAnyVariableWithNameExisting = variablesWithName.stream()
                .anyMatch(variable -> variable.getName().equals(name) && !variable.getId().equals(id));

        if (isAnyVariableWithNameExisting) {
            throw new DuplicatedVariableNameException(name);
        }
    }

    private void publishVariables() {
        Collection<Variable> variables = getAll();
        pdpConfigurationPublisher.publishVariables(variables);
    }
}
