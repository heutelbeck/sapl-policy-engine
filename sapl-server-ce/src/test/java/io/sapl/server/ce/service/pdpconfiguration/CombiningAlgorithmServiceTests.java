package io.sapl.server.ce.service.pdpconfiguration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.model.pdpconfiguration.SelectedCombiningAlgorithm;
import io.sapl.server.ce.pdp.PDPConfigurationPublisher;
import io.sapl.server.ce.persistence.SelectedCombiningAlgorithmRepository;

@ExtendWith(MockitoExtension.class)
public class CombiningAlgorithmServiceTests {
    private SelectedCombiningAlgorithmRepository selectedCombiningAlgorithmRepository;
    private PDPConfigurationPublisher pdpConfigurationPublisher;

    @BeforeEach
    public void beforeEach() {
        selectedCombiningAlgorithmRepository = mock(SelectedCombiningAlgorithmRepository.class);
        pdpConfigurationPublisher = mock(PDPConfigurationPublisher.class);
    }

    @Test
    public void init() {
        CombiningAlgorithmService combiningAlgorithmService = getCombiningAlgorithmService();

        SelectedCombiningAlgorithm entity = new SelectedCombiningAlgorithm();
        entity.setId((long)1);
        entity.setSelection(PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE);

        when(selectedCombiningAlgorithmRepository.findAll()).thenReturn(
                Collections.singletonList(entity));
        combiningAlgorithmService.init();
        verify(selectedCombiningAlgorithmRepository, times(1)).findAll();
    }

    @Test
    public void getSelected() {
        CombiningAlgorithmService combiningAlgorithmService = getCombiningAlgorithmService();

        SelectedCombiningAlgorithm entity = new SelectedCombiningAlgorithm();
        entity.setId((long)1);
        entity.setSelection(PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE);

        when(selectedCombiningAlgorithmRepository.findAll()).thenReturn(
                Collections.singletonList(entity));
        PolicyDocumentCombiningAlgorithm selectedCombiningAlgorithm = combiningAlgorithmService.getSelected();
        assertEquals(entity.getSelection(), selectedCombiningAlgorithm);
    }

    @Test
    public void getSelected_noExistingEntity() {
        CombiningAlgorithmService combiningAlgorithmService = getCombiningAlgorithmService();

        when(selectedCombiningAlgorithmRepository.findAll()).thenReturn(Collections.emptyList());
        PolicyDocumentCombiningAlgorithm selectedCombiningAlgorithm = combiningAlgorithmService.getSelected();
        assertEquals(CombiningAlgorithmService.DEFAULT, selectedCombiningAlgorithm);
    }

    @Test
    public void getSelected_moreThanOneEntity() {
        CombiningAlgorithmService combiningAlgorithmService = getCombiningAlgorithmService();

        SelectedCombiningAlgorithm entity = new SelectedCombiningAlgorithm();
        entity.setId((long)1);
        entity.setSelection(PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE);
        SelectedCombiningAlgorithm otherEntity = new SelectedCombiningAlgorithm();
        entity.setId((long)2);
        entity.setSelection(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);

        when(selectedCombiningAlgorithmRepository.findAll()).thenReturn(
                Arrays.asList(entity, otherEntity));
        PolicyDocumentCombiningAlgorithm selectedCombiningAlgorithm = combiningAlgorithmService.getSelected();
        assertEquals(entity.getSelection(), selectedCombiningAlgorithm);
    }

    @Test
    public void getAvailable() {
        CombiningAlgorithmService combiningAlgorithmService = getCombiningAlgorithmService();

        assertArrayEquals(PolicyDocumentCombiningAlgorithm.values(), combiningAlgorithmService.getAvailable());
    }

    @Test
    public void setSelected() {
        CombiningAlgorithmService combiningAlgorithmService = getCombiningAlgorithmService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            combiningAlgorithmService.setSelected(null);
        });

        int invocationCounter = 0;
        for (PolicyDocumentCombiningAlgorithm algorithm : PolicyDocumentCombiningAlgorithm.values()) {
            invocationCounter++;

            combiningAlgorithmService.setSelected(algorithm);

            verify(selectedCombiningAlgorithmRepository, times(invocationCounter)).deleteAll();
            verify(selectedCombiningAlgorithmRepository, times(1)).save(new SelectedCombiningAlgorithm(algorithm));
            verify(pdpConfigurationPublisher, times(1)).publishCombiningAlgorithm(algorithm);
        }
    }

    private CombiningAlgorithmService getCombiningAlgorithmService() {
        return new CombiningAlgorithmService(selectedCombiningAlgorithmRepository, pdpConfigurationPublisher);
    }
}
