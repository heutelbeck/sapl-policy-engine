package io.sapl.server.ce.service.pdpconfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.pdp.PDPConfigurationPublisher;
import io.sapl.server.ce.persistence.VariablesRepository;

@ExtendWith(MockitoExtension.class)
public class VariablesServiceTests {
    private VariablesRepository variableRepository;
    private PDPConfigurationPublisher pdpConfigurationPublisher;

    @BeforeEach
    public void beforeEach() {
        variableRepository = mock(VariablesRepository.class);
        pdpConfigurationPublisher = mock(PDPConfigurationPublisher.class);
    }

    @Test
    public void init() {
        VariablesService variablesService = getVariablesService();

        Collection<Variable> availableVariables = Arrays.asList(
                new Variable().setId((long)1).setName("foo").setJsonValue(VariablesService.DEFAULT_JSON_VALUE),
                new Variable().setId((long)2).setName("bar").setJsonValue(VariablesService.DEFAULT_JSON_VALUE));
        when(variableRepository.findAll()).thenReturn(availableVariables);
        variablesService.init();
        verify(pdpConfigurationPublisher, times(1)).publishVariables(availableVariables);
    }

    @Test
    public void getAll() {
        VariablesService variablesService = getVariablesService();

        Collection<Variable> expectedVariables = Arrays.asList(new Variable(), new Variable());
        when(variableRepository.findAll()).thenReturn(expectedVariables);

        assertEquals(expectedVariables, variablesService.getAll());
    }

    @Test
    public void getAmount() {
        VariablesService variablesService = getVariablesService();

        when(variableRepository.count()).thenReturn((long)0);
        assertEquals(0, variablesService.getAmount());

        when(variableRepository.count()).thenReturn((long)1);
        assertEquals(1, variablesService.getAmount());

        when(variableRepository.count()).thenReturn((long)19);
        assertEquals(19, variablesService.getAmount());
    }

    @Test
    public void getById() {
        final long available = 1;
        final long nonAvailable = 2;
        final Variable availableVariable = new Variable();

        when(variableRepository.findById(available)).thenReturn(Optional.of(availableVariable));
        when(variableRepository.findById(nonAvailable)).thenReturn(Optional.empty());

        VariablesService variablesService = getVariablesService();
        assertEquals(Optional.of(availableVariable), variablesService.getById(available));
        assertEquals(Optional.empty(), variablesService.getById(nonAvailable));
    }

    @Test
    public void create() throws DuplicatedVariableNameException, InvalidVariableNameException {
        final String firstVariableName = "first";
        final String secondVariableName = "second";

        VariablesService variablesService = getVariablesService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            variablesService.create(null);
        });

        Variable firstVariable = variablesService.create(firstVariableName);
        verify(variableRepository, times(1)).save(any());
        assertEquals(firstVariableName, firstVariable.getName());
        assertEquals(VariablesService.DEFAULT_JSON_VALUE, firstVariable.getJsonValue());

        Variable secondVariable = variablesService.create(secondVariableName);
        verify(variableRepository, times(2)).save(any());
        assertEquals(secondVariableName, secondVariable.getName());
        assertEquals(VariablesService.DEFAULT_JSON_VALUE, secondVariable.getJsonValue());
    }

    @Test
    public void create_invalidName() {
        VariablesService variablesService = getVariablesService();

        Assertions.assertThrows(InvalidVariableNameException.class, () -> {
            variablesService.create(StringUtils.repeat("*", VariablesService.MIN_NAME_LENGTH - 1));
        });
        Assertions.assertThrows(InvalidVariableNameException.class, () -> {
            variablesService.create(StringUtils.repeat("*", VariablesService.MAX_NAME_LENGTH + 1));
        });
    }

    @Test
    public void create_duplicatedName() {
        final Variable conflictingVariable = new Variable()
                .setId((long)1)
                .setName("foo")
                .setJsonValue("{}}");

        VariablesService variablesService = getVariablesService();

        when(variableRepository.findByName(conflictingVariable.getName()))
                .thenReturn(Collections.singletonList(conflictingVariable));
        Assertions.assertThrows(DuplicatedVariableNameException.class, () -> {
            variablesService.create(conflictingVariable.getName());
        });
    }

    @Test
    public void edit() throws InvalidVariableNameException, InvalidJsonException, DuplicatedVariableNameException {
        final Variable existingVariable = new Variable()
                .setId((long)1)
                .setName("foo")
                .setJsonValue(VariablesService.DEFAULT_JSON_VALUE);
        final String newName = "bar";
        final String newJsonValue = "{ \"foo\" : \"bar\" }";

        VariablesService variablesService = getVariablesService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            variablesService.edit(1, null, "json");
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            variablesService.edit(1, "name", null);
        });

        when(variableRepository.findById(existingVariable.getId())).thenReturn(Optional.of(existingVariable));
        Variable editedVariable = variablesService.edit(existingVariable.getId(), newName, newJsonValue);
        verify(variableRepository).save(new Variable(existingVariable.getId(), newName, newJsonValue));
        assertEquals(existingVariable.getId(), editedVariable.getId());
        assertEquals(newName, editedVariable.getName());
        assertEquals(newJsonValue, editedVariable.getJsonValue());
    }

    @Test
    public void edit_variableNotExisting() {
        final long nonExistingVariableId = 1;

        VariablesService variablesService = getVariablesService();

        when(variableRepository.findById(nonExistingVariableId)).thenReturn(Optional.empty());
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            variablesService.edit(nonExistingVariableId, "foo", VariablesService.DEFAULT_JSON_VALUE);
        });
    }

    @Test
    public void edit_invalidJson() {
        VariablesService variablesService = getVariablesService();

        Assertions.assertThrows(InvalidJsonException.class, () -> {
            variablesService.edit(1, "foo", "invalidJson");
        });
        Assertions.assertThrows(InvalidJsonException.class, () -> {
            variablesService.edit(1, "foo", "");
        });
    }

    @Test
    public void edit_invalidName() {
        final long idOfVariableToEdit = 1;

        VariablesService variablesService = getVariablesService();

        Assertions.assertThrows(InvalidVariableNameException.class, () -> {
            variablesService.edit(
                    idOfVariableToEdit,
                    StringUtils.repeat("*", VariablesService.MIN_NAME_LENGTH - 1),
                    VariablesService.DEFAULT_JSON_VALUE);
        });
        Assertions.assertThrows(InvalidVariableNameException.class, () -> {
            variablesService.edit(
                    idOfVariableToEdit,
                    StringUtils.repeat("*", VariablesService.MAX_NAME_LENGTH + 1),
                    VariablesService.DEFAULT_JSON_VALUE);
        });
    }

    @Test
    public void edit_duplicatedName() {
        final long idOfVariableToEdit = 1;
        final Variable conflictingVariable = new Variable()
                .setId((long)2)
                .setName("foo")
                .setJsonValue("{}}");

        VariablesService variablesService = getVariablesService();

        when(variableRepository.findByName(conflictingVariable.getName()))
                .thenReturn(Collections.singletonList(conflictingVariable));
        Assertions.assertThrows(DuplicatedVariableNameException.class, () -> {
            variablesService.edit(
                    idOfVariableToEdit, conflictingVariable.getName(), VariablesService.DEFAULT_JSON_VALUE);
        });
    }

    @Test
    public void delete() {
        final Variable variableToDelete = new Variable()
                .setId((long)1)
                .setName("foo")
                .setJsonValue(VariablesService.DEFAULT_JSON_VALUE);

        VariablesService variablesService = getVariablesService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            variablesService.delete(null);
        });

        when(variableRepository.findById(variableToDelete.getId())).thenReturn(Optional.of(variableToDelete));
        variablesService.delete(variableToDelete.getId());
        verify(variableRepository, times(1)).deleteById(variableToDelete.getId());
    }

    @Test
    public void delete_variableNotExisting() {
        final long nonExistingVariableId = 1;

        VariablesService variablesService = getVariablesService();

        when(variableRepository.findById(nonExistingVariableId)).thenReturn(Optional.empty());
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            variablesService.delete(nonExistingVariableId);
        });
    }

    private VariablesService getVariablesService() {
        return new VariablesService(variableRepository, pdpConfigurationPublisher);
    }
}
