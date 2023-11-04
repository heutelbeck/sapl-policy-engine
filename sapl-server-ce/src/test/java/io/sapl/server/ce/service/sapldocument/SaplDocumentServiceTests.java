package io.sapl.server.ce.service.sapldocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import io.sapl.interpreter.DocumentAnalysisResult;
import io.sapl.interpreter.DocumentType;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentVersion;
import io.sapl.server.ce.persistence.PublishedSaplDocumentRepository;
import io.sapl.server.ce.persistence.SaplDocumentsRepository;
import io.sapl.server.ce.persistence.SaplDocumentsVersionRepository;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class SaplDocumentServiceTests {
    private SaplDocumentsRepository saplDocumentRepository;
    private SaplDocumentsVersionRepository saplDocumentVersionRepository;
    private PublishedSaplDocumentRepository publishedSaplDocumentRepository;
    private SAPLInterpreter saplInterpreter;

    @BeforeEach
    public void beforeEach() {
        saplDocumentRepository = mock(SaplDocumentsRepository.class);
        saplDocumentVersionRepository = mock(SaplDocumentsVersionRepository.class);
        publishedSaplDocumentRepository = mock(PublishedSaplDocumentRepository.class);
        saplInterpreter = mock(SAPLInterpreter.class);
    }

    @Test
    public void getUpdates() {
        SaplDocumentService saplDocumentService = null;
        try {
            saplDocumentService = getSaplDocumentService();
            saplDocumentService.init();

            Flux<PrpUpdateEvent> flux = saplDocumentService.getUpdates();
            assertNotNull(flux);

            PrpUpdateEvent initialEvent = flux.blockFirst();
            assertNotNull(initialEvent);
            PrpUpdateEvent.Update[] updatesOfInitialEvent = initialEvent.getUpdates();
            assertNotNull(updatesOfInitialEvent);
            assertEquals(0, updatesOfInitialEvent.length);
        } finally {
            if (saplDocumentService != null) {
                saplDocumentService.dispose();
            }
        }
    }

    @Test
    public void getAll() {
        SaplDocumentService saplDocumentService = getSaplDocumentService();

        Collection<SaplDocument> expectedSaplDocuments, actualSaplDocuments;

        expectedSaplDocuments = Collections.emptyList();
        when(saplDocumentRepository.findAll()).thenReturn(expectedSaplDocuments);
        actualSaplDocuments = saplDocumentService.getAll();
        verify(saplDocumentRepository, times(1)).findAll();
        assertEquals(expectedSaplDocuments, actualSaplDocuments);

        expectedSaplDocuments = Collections.singletonList(new SaplDocument());
        when(saplDocumentRepository.findAll()).thenReturn(expectedSaplDocuments);
        actualSaplDocuments = saplDocumentService.getAll();
        verify(saplDocumentRepository, times(2)).findAll();
        assertEquals(expectedSaplDocuments, actualSaplDocuments);

        expectedSaplDocuments = Arrays.asList(new SaplDocument(), new SaplDocument());
        when(saplDocumentRepository.findAll()).thenReturn(expectedSaplDocuments);
        actualSaplDocuments = saplDocumentService.getAll();
        verify(saplDocumentRepository, times(3)).findAll();
        assertEquals(expectedSaplDocuments, actualSaplDocuments);
    }

    @Test
    void getById() {
        final SaplDocument expectedSaplDocument = new SaplDocument()
                .setId((long)1);

        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(saplDocumentRepository.findById(expectedSaplDocument.getId()))
                .thenReturn(Optional.of(expectedSaplDocument));
        Optional<SaplDocument> actualOptionalSaplDocument = saplDocumentService.getById(expectedSaplDocument.getId());
        verify(saplDocumentRepository, times(1)).findById(expectedSaplDocument.getId());
        assertTrue(actualOptionalSaplDocument.isPresent());
        assertEquals(expectedSaplDocument, actualOptionalSaplDocument.get());
    }

    @Test
    public void getById_notExistingSaplDocument() {
        final long idOfNotExistingSaplDocument = 91;

        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(saplDocumentRepository.findById(idOfNotExistingSaplDocument))
                .thenReturn(Optional.empty());
        assertEquals(Optional.empty(), saplDocumentService.getById(idOfNotExistingSaplDocument));
    }

    @Test
    public void getAmount() {
        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(saplDocumentRepository.count()).thenReturn((long)0);
        assertEquals(0, saplDocumentService.getAmount());
        verify(saplDocumentRepository, times(1)).count();

        when(saplDocumentRepository.count()).thenReturn((long)1);
        assertEquals(1, saplDocumentService.getAmount());
        verify(saplDocumentRepository, times(2)).count();

        when(saplDocumentRepository.count()).thenReturn((long)2);
        assertEquals(2, saplDocumentService.getAmount());
        verify(saplDocumentRepository, times(3)).count();
    }

    @Test
    public void createDefault() {
        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(saplInterpreter.analyze(SaplDocumentService.DEFAULT_DOCUMENT_VALUE))
                .thenReturn(new DocumentAnalysisResult(true, "all deny", DocumentType.POLICY, null));
        when(saplDocumentRepository.save(any()))
                .thenAnswer(i -> i.getArguments()[0]);
        when(saplDocumentVersionRepository.save(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        for (int i = 1; i < 5 + 1; i++) {
            SaplDocument saplDocument = saplDocumentService.createDefault();
            verify(saplInterpreter, times(i))
                    .analyze(SaplDocumentService.DEFAULT_DOCUMENT_VALUE);
            verify(saplDocumentRepository, times(i))
                    .save(any());
            verify(saplDocumentVersionRepository, times(i))
                    .save(any());
            assertNotNull(saplDocument);
        }
    }

    @Test
    public void createVersion() {
        final SaplDocument saplDocument = new SaplDocument()
                .setId((long)1)
                .setCurrentVersionNumber(1)
                .setVersions(Collections.singletonList(new SaplDocumentVersion().setVersionNumber(1)));
        final String saplDocumentValue = "document";

        when(saplDocumentRepository.findById(saplDocument.getId()))
                .thenReturn(Optional.of(saplDocument));
        when(saplInterpreter.analyze(saplDocumentValue))
                .thenReturn(new DocumentAnalysisResult(true, saplDocumentValue, DocumentType.POLICY, null));

        SaplDocumentService saplDocumentService = getSaplDocumentService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            saplDocumentService.createVersion(saplDocument.getId(), null);
        });

        SaplDocumentVersion newVersion =
                saplDocumentService.createVersion(saplDocument.getId(), saplDocumentValue);
        verify(saplInterpreter, times(1))
                .analyze(newVersion.getValue());
        verify(saplDocumentRepository, times(1))
                .save(any());
        verify(saplDocumentVersionRepository, times(1))
                .save(any());
        assertEquals(2, newVersion.getVersionNumber());
    }

    @Test
    public void createVersion_notExistingSaplDocument() {
        final long idOfNotExistingSaplDocument = 1;

        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(saplDocumentRepository.findById(idOfNotExistingSaplDocument))
                .thenReturn(Optional.empty());
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            saplDocumentService.createVersion(
                    idOfNotExistingSaplDocument,
                    SaplDocumentService.DEFAULT_DOCUMENT_VALUE);
        });
    }

    @Test
    public void createVersion_invalidSaplDocument() {
        final long saplDocumentId = 1;

        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(saplDocumentRepository.findById(saplDocumentId))
                .thenReturn(Optional.of(new SaplDocument()));
        when(saplInterpreter.analyze(any()))
                .thenReturn(new DocumentAnalysisResult(false, null, null, null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            saplDocumentService.createVersion(saplDocumentId, "invalid");
        });
    }

    @Test
    public void publishAndUnpublishPolicyVersion() throws PublishedDocumentNameCollisionException {
        final SaplDocument saplDocument = new SaplDocument();
        final SaplDocumentVersion firstVersion = new SaplDocumentVersion()
                .setId((long)1)
                .setVersionNumber(1)
                .setName("foo name")
                .setValue("foo")
                .setSaplDocument(saplDocument);
        final SaplDocumentVersion secondVersion = new SaplDocumentVersion()
                .setId((long)2)
                .setVersionNumber(2)
                .setName("foo name")
                .setValue("foo extended")
                .setSaplDocument(saplDocument);
        saplDocument
                .setId((long)1)
                .setVersions(Arrays.asList(firstVersion, secondVersion));

        when(saplDocumentRepository.findById(saplDocument.getId()))
                .thenReturn(Optional.of(saplDocument));
        when(publishedSaplDocumentRepository.save(any()))
                .thenAnswer(i -> i.getArguments()[0]);

        List<PrpUpdateEvent> prpUpdateEvents;
        PrpUpdateEvent relevantPrpUpdateEvent;
        PrpUpdateEvent.Update[] relevantUpdates;

        SaplDocumentService saplDocumentService = getSaplDocumentService();
        saplDocumentService.init();

        // publish first version
        saplDocumentService.publishPolicyVersion(saplDocument.getId(), firstVersion.getVersionNumber());
        verify(saplDocumentRepository, times(1)).save(any());
        verify(publishedSaplDocumentRepository, times(1)).save(any());
        assertEquals(firstVersion, saplDocument.getPublishedVersion());

        // check PRP update for published first version
        prpUpdateEvents = saplDocumentService.getUpdates().take(2).collectList().block();
        assertNotNull(prpUpdateEvents);
        assertEquals(2, prpUpdateEvents.size());
        relevantPrpUpdateEvent = prpUpdateEvents.get(1);
        relevantUpdates = relevantPrpUpdateEvent.getUpdates();
        assertNotNull(relevantUpdates);
        assertEquals(1, relevantUpdates.length);
        assertEquals(firstVersion.getValue(), relevantUpdates[0].getRawDocument());
        assertEquals(PrpUpdateEvent.Type.PUBLISH, relevantUpdates[0].getType());

        when(publishedSaplDocumentRepository.findByDocumentName(firstVersion.getName()))
                .thenReturn(Collections.singletonList(new PublishedSaplDocument()
                        .setDocumentName(firstVersion.getName())
                        .setDocument(firstVersion.getValue())));

        // publish second version
        saplDocumentService.publishPolicyVersion(saplDocument.getId(), secondVersion.getVersionNumber());
        verify(saplDocumentRepository, times(3)).save(any());
        verify(publishedSaplDocumentRepository, times(2)).save(any());
        assertEquals(secondVersion, saplDocument.getPublishedVersion());

        // check PRP update for published second version
        prpUpdateEvents = saplDocumentService.getUpdates().take(4).collectList().block();
        assertNotNull(prpUpdateEvents);
        assertEquals(4, prpUpdateEvents.size());

        relevantPrpUpdateEvent = prpUpdateEvents.get(2);
        relevantUpdates = relevantPrpUpdateEvent.getUpdates();
        assertNotNull(relevantUpdates);
        assertEquals(1, relevantUpdates.length);
        assertEquals(firstVersion.getValue(), relevantUpdates[0].getRawDocument());
        assertEquals(PrpUpdateEvent.Type.WITHDRAW, relevantUpdates[0].getType());

        relevantPrpUpdateEvent = prpUpdateEvents.get(3);
        relevantUpdates = relevantPrpUpdateEvent.getUpdates();
        assertNotNull(relevantUpdates);
        assertEquals(1, relevantUpdates.length);
        assertEquals(secondVersion.getValue(), relevantUpdates[0].getRawDocument());
        assertEquals(PrpUpdateEvent.Type.PUBLISH, relevantUpdates[0].getType());
    }

    @Test
    public void publishPolicyVersion_preventCollingVersion() {
        final SaplDocument saplDocument = new SaplDocument();
        final SaplDocumentVersion version = new SaplDocumentVersion()
                .setId((long)2)
                .setVersionNumber(1)
                .setName("foo name")
                .setValue("foo")
                .setSaplDocument(saplDocument);
        saplDocument
                .setId((long)1)
                .setVersions(List.of(version));

        when(saplDocumentRepository.findById(saplDocument.getId())).thenReturn(Optional.of(saplDocument));

        // calling findAll is used to force checking database constraints after creating published version
        when(publishedSaplDocumentRepository.findAll()).thenThrow(new DataIntegrityViolationException("invalid"));

        SaplDocumentService saplDocumentService = getSaplDocumentService();
        Assertions.assertThrows(PublishedDocumentNameCollisionException.class, () -> {
            saplDocumentService.publishPolicyVersion(saplDocument.getId(), version.getVersionNumber());
        });
    }

    @Test
    public void unpublishPolicy_versionNotPublished() {
        final SaplDocument saplDocument = new SaplDocument();
        final SaplDocumentVersion version = new SaplDocumentVersion()
                .setId((long)1)
                .setVersionNumber(1)
                .setName("foo name")
                .setValue("foo")
                .setSaplDocument(saplDocument);
        saplDocument
                .setId((long)1)
                .setVersions(Collections.singletonList(version));

        when(saplDocumentRepository.findById(saplDocument.getId()))
                .thenReturn(Optional.of(saplDocument));

        SaplDocumentService saplDocumentService = getSaplDocumentService();
        saplDocumentService.unpublishPolicy(saplDocument.getId());

        verify(saplDocumentRepository, times(1))
                .findById(saplDocument.getId());
    }

    @Test
    public void getPublishedSaplDocuments() {
        Collection<PublishedSaplDocument> expectedPublishedSaplDocuments;

        SaplDocumentService saplDocumentService = getSaplDocumentService();

        expectedPublishedSaplDocuments = Collections.emptyList();
        when(publishedSaplDocumentRepository.findAll())
                .thenReturn(expectedPublishedSaplDocuments);
        assertEquals(
                expectedPublishedSaplDocuments,
                saplDocumentService.getPublishedSaplDocuments());

        expectedPublishedSaplDocuments = Collections.singletonList(new PublishedSaplDocument());
        when(publishedSaplDocumentRepository.findAll())
                .thenReturn(expectedPublishedSaplDocuments);
        assertEquals(
                expectedPublishedSaplDocuments,
                saplDocumentService.getPublishedSaplDocuments());

        expectedPublishedSaplDocuments = Arrays.asList(new PublishedSaplDocument(), new PublishedSaplDocument());
        when(publishedSaplDocumentRepository.findAll())
                .thenReturn(expectedPublishedSaplDocuments);
        assertEquals(
                expectedPublishedSaplDocuments,
                saplDocumentService.getPublishedSaplDocuments());
    }

    @Test
    public void getPublishedAmount() {
        SaplDocumentService saplDocumentService = getSaplDocumentService();

        when(publishedSaplDocumentRepository.count()).thenReturn((long)0);
        assertEquals(0, saplDocumentService.getPublishedAmount());

        when(publishedSaplDocumentRepository.count()).thenReturn((long)1);
        assertEquals(1, saplDocumentService.getPublishedAmount());

        when(publishedSaplDocumentRepository.count()).thenReturn((long)19);
        assertEquals(19, saplDocumentService.getPublishedAmount());
    }

    private SaplDocumentService getSaplDocumentService() {
        return new SaplDocumentService(
                saplDocumentRepository,
                saplDocumentVersionRepository,
                publishedSaplDocumentRepository,
                saplInterpreter);
    }
}
