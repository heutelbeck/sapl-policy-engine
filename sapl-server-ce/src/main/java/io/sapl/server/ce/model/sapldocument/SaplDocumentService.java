/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.model.sapldocument;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.sapl.interpreter.DocumentType;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.Many;

/**
 * Service for reading and managing {@link SaplDocument} instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class SaplDocumentService implements PrpUpdateEventSource {
    public static final String DEFAULT_DOCUMENT_VALUE = "policy \"all deny\"\ndeny";

    private final SaplDocumentsRepository         saplDocumentRepository;
    private final SaplDocumentsVersionRepository  saplDocumentVersionRepository;
    private final PublishedSaplDocumentRepository publishedSaplDocumentRepository;
    private final SAPLInterpreter                 saplInterpreter;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.GERMANY).withZone(ZoneId.systemDefault());

    private Many<PrpUpdateEvent> prpUpdateEventSink = Sinks.many().replay().all();

    @PostConstruct
    public void init() {
        // emit initial event
        PrpUpdateEvent initialEvent = generateInitialPrpUpdateEvent();
        prpUpdateEventSink.emitNext(initialEvent, EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public Flux<PrpUpdateEvent> getUpdates() {
        return prpUpdateEventSink.asFlux();
    }

    @Override
    public void dispose() {
        // NOOP
    }

    public Collection<SaplDocument> getAll() {
        return saplDocumentRepository.findAll();
    }

    public Optional<SaplDocument> getById(long id) {
        return saplDocumentRepository.findById(id);
    }

    public long getAmount() {
        return saplDocumentRepository.count();
    }

    public SaplDocument createDefault() {
        String documentValue = DEFAULT_DOCUMENT_VALUE;

        Document documentAnalysisResult = saplInterpreter.parseDocument(documentValue);

        DocumentType type = documentAnalysisResult.type();
        String       name = documentAnalysisResult.name();

        SaplDocument saplDocumentToCreate = new SaplDocument().setLastModified(getCurrentTimestampAsString())
                .setName(name).setCurrentVersionNumber(1).setType(type);
        SaplDocument createdDocument      = saplDocumentRepository.save(saplDocumentToCreate);

        SaplDocumentVersion initialSaplDocumentVersion = new SaplDocumentVersion().setSaplDocument(createdDocument)
                .setVersionNumber(1).setDocumentContent(documentValue).setName(name);
        saplDocumentVersionRepository.save(initialSaplDocumentVersion);

        return createdDocument;
    }

    public SaplDocumentVersion createVersion(long saplDocumentId, @NonNull String documentValue) {
        SaplDocument saplDocument = getExistingById(saplDocumentId);

        Document documentAnalysisResult = saplInterpreter.parseDocument(documentValue);
        if (documentAnalysisResult.isInvalid()) {
            throw new IllegalArgumentException(String.format("document value is invalid (value: %s)", documentValue));
        }

        int          newVersionNumber = saplDocument.getCurrentVersionNumber() + 1;
        DocumentType type             = documentAnalysisResult.type();
        String       newName          = documentAnalysisResult.name();

        SaplDocumentVersion newSaplDocumentVersion = new SaplDocumentVersion().setSaplDocument(saplDocument)
                .setVersionNumber(newVersionNumber).setDocumentContent(documentValue).setName(newName);
        saplDocumentVersionRepository.save(newSaplDocumentVersion);

        saplDocument.setCurrentVersionNumber(newVersionNumber).setLastModified(getCurrentTimestampAsString())
                .setType(type).setName(newName);
        saplDocumentRepository.save(saplDocument);

        return newSaplDocumentVersion;
    }

    @Transactional(rollbackFor = Throwable.class)
    public void publishPolicyVersion(long saplDocumentId, int versionToPublish)
            throws PublishedDocumentNameCollisionException {
        SaplDocument saplDocument = getExistingById(saplDocumentId);
        final var    updateEvents = new ArrayList<Update>(2);

        // unpublish other version if published
        if (saplDocument.getPublishedVersion() != null) {
            SaplDocument saplDocumentToUnpublish = getExistingById(saplDocumentId);

            SaplDocumentVersion publishedVersion = saplDocumentToUnpublish.getPublishedVersion();
            if (publishedVersion != null) {

                // update persisted published documents
                Iterable<PublishedSaplDocument> deletedPublishedSaplDocuments = deletePersistedPublishedSaplDocumentsByName(
                        publishedVersion.getName());
                for (var doc : deletedPublishedSaplDocuments) {
                    updateEvents.add(convertSaplDocumentToUpdateOfPrpUpdateEvent(doc, PrpUpdateEvent.Type.WITHDRAW));
                }

                // update persisted document
                saplDocumentToUnpublish.setPublishedVersion(null);
                saplDocumentRepository.save(saplDocumentToUnpublish);

                log.info("unpublish version {} of SAPL document with id {} (name: {})",
                        publishedVersion.getVersionNumber(), saplDocumentId, saplDocumentToUnpublish.getName());
            }
        }

        SaplDocumentVersion saplDocumentVersionToPublish = saplDocument.getVersion(versionToPublish);

        // update persisted published documents
        PublishedSaplDocument createdPublishedSaplDocument = createPersistedPublishedSaplDocument(
                saplDocumentVersionToPublish);

        // update persisted document
        saplDocument.setPublishedVersion(saplDocumentVersionToPublish).setLastModified(getCurrentTimestampAsString());
        saplDocumentRepository.save(saplDocument);

        log.info("publish version {} of SAPL document with id {} (name: {})",
                saplDocumentVersionToPublish.getVersionNumber(), saplDocumentId,
                saplDocumentVersionToPublish.getName());

        updateEvents.add(
                convertSaplDocumentToUpdateOfPrpUpdateEvent(createdPublishedSaplDocument, PrpUpdateEvent.Type.PUBLISH));

        final var prpUpdateEvent = new PrpUpdateEvent(updateEvents);
        prpUpdateEventSink.emitNext(prpUpdateEvent, EmitFailureHandler.FAIL_FAST);
    }

    @Transactional
    public void unpublishPolicy(long saplDocumentId) {
        SaplDocument saplDocumentToUnpublish = getExistingById(saplDocumentId);

        SaplDocumentVersion publishedVersion = saplDocumentToUnpublish.getPublishedVersion();
        if (publishedVersion == null) {
            return;
        }

        // update persisted published documents
        Iterable<PublishedSaplDocument> deletedPublishedSaplDocuments = deletePersistedPublishedSaplDocumentsByName(
                publishedVersion.getName());

        // update persisted document
        saplDocumentToUnpublish.setPublishedVersion(null);
        saplDocumentRepository.save(saplDocumentToUnpublish);

        log.info("unpublish version {} of SAPL document with id {} (name: {})", publishedVersion.getVersionNumber(),
                saplDocumentId, saplDocumentToUnpublish.getName());

        notifyAboutChangedPublicationOfSaplDocument(PrpUpdateEvent.Type.WITHDRAW, deletedPublishedSaplDocuments);
    }

    public Collection<PublishedSaplDocument> getPublishedSaplDocuments() {
        return publishedSaplDocumentRepository.findAll();
    }

    public long getPublishedAmount() {
        return publishedSaplDocumentRepository.count();
    }

    private SaplDocument getExistingById(long saplDocumentId) {
        Optional<SaplDocument> optionalSaplDocument = getById(saplDocumentId);
        if (optionalSaplDocument.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("SAPL document with id %d is not available", saplDocumentId));
        }

        return optionalSaplDocument.get();
    }

    private String getCurrentTimestampAsString() {
        return dateFormatter.format(Instant.now());
    }

    private PrpUpdateEvent generateInitialPrpUpdateEvent() {
        List<PrpUpdateEvent.Update> updates = publishedSaplDocumentRepository.findAll().stream()
                .map(publishedSaplDocument -> convertSaplDocumentToUpdateOfPrpUpdateEvent(publishedSaplDocument,
                        PrpUpdateEvent.Type.PUBLISH))
                .toList();
        return new PrpUpdateEvent(updates);
    }

    private PrpUpdateEvent.Update convertSaplDocumentToUpdateOfPrpUpdateEvent(
            PublishedSaplDocument publishedSaplDocument, PrpUpdateEvent.Type prpUpdateEventType) {
        Document document = saplInterpreter.parseDocument(publishedSaplDocument.getDocument());
        return new Update(prpUpdateEventType, document);
    }

    private Iterable<PublishedSaplDocument> deletePersistedPublishedSaplDocumentsByName(String name) {
        Collection<PublishedSaplDocument> publishedDocumentsWithName = publishedSaplDocumentRepository
                .findByDocumentName(name);
        publishedSaplDocumentRepository.deleteAll(publishedDocumentsWithName);

        return publishedDocumentsWithName;
    }

    private PublishedSaplDocument createPersistedPublishedSaplDocument(SaplDocumentVersion saplDocumentVersion)
            throws PublishedDocumentNameCollisionException {
        PublishedSaplDocument publishedSaplDocument = new PublishedSaplDocument();
        publishedSaplDocument.importSaplDocumentVersion(saplDocumentVersion);

        PublishedSaplDocument createdPublishedSaplDocument = publishedSaplDocumentRepository
                .save(publishedSaplDocument);

        // enforce checking constraints in transaction
        try {
            publishedSaplDocumentRepository.findAll();
        } catch (DataIntegrityViolationException ex) {
            throw new PublishedDocumentNameCollisionException(saplDocumentVersion.getName(), ex);
        }

        return createdPublishedSaplDocument;
    }

    private void notifyAboutChangedPublicationOfSaplDocument(PrpUpdateEvent.Type prpUpdateEventType,
            Iterable<PublishedSaplDocument> publishedSaplDocuments) {
        List<PrpUpdateEvent.Update> updateEvents = Streamable.of(publishedSaplDocuments)
                .map(publishedSaplDocument -> convertSaplDocumentToUpdateOfPrpUpdateEvent(publishedSaplDocument,
                        prpUpdateEventType))
                .toList();

        PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updateEvents);
        prpUpdateEventSink.emitNext(prpUpdateEvent, EmitFailureHandler.FAIL_FAST);
    }
}
