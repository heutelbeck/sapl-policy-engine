/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.Document;
import io.sapl.parser.DocumentType;
import io.sapl.parser.SAPLParser;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;

/**
 * Service for reading and managing {@link SaplDocument} instances.
 * Uses the ANTLR-based parser for document validation.
 */
@Slf4j
@Service
@Conditional(SetupFinishedCondition.class)
public class SaplDocumentService {
    public static final String DEFAULT_DOCUMENT_VALUE = "policy \"all deny\"\ndeny";

    private final SaplDocumentsRepository         saplDocumentRepository;
    private final SaplDocumentsVersionRepository  saplDocumentVersionRepository;
    private final PublishedSaplDocumentRepository publishedSaplDocumentRepository;
    private final SAPLParser                      saplParser;
    private final List<PolicyChangeListener>      policyChangeListeners;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.GERMANY).withZone(ZoneId.systemDefault());

    public SaplDocumentService(SaplDocumentsRepository saplDocumentRepository,
            SaplDocumentsVersionRepository saplDocumentVersionRepository,
            PublishedSaplDocumentRepository publishedSaplDocumentRepository,
            List<PolicyChangeListener> policyChangeListeners) {
        this.saplDocumentRepository          = saplDocumentRepository;
        this.saplDocumentVersionRepository   = saplDocumentVersionRepository;
        this.publishedSaplDocumentRepository = publishedSaplDocumentRepository;
        this.saplParser                      = new DefaultSAPLParser();
        this.policyChangeListeners           = policyChangeListeners != null ? policyChangeListeners : List.of();
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
        var documentValue = DEFAULT_DOCUMENT_VALUE;

        var parsedDocument = saplParser.parseDocument(documentValue);

        var type = parsedDocument.type();
        var name = parsedDocument.name();

        var saplDocumentToCreate = new SaplDocument().setLastModified(getCurrentTimestampAsString()).setName(name)
                .setCurrentVersionNumber(1).setType(type);
        var createdDocument      = saplDocumentRepository.save(saplDocumentToCreate);

        var initialSaplDocumentVersion = new SaplDocumentVersion().setSaplDocument(createdDocument).setVersionNumber(1)
                .setDocumentContent(documentValue).setName(name);
        saplDocumentVersionRepository.save(initialSaplDocumentVersion);

        return createdDocument;
    }

    public SaplDocumentVersion createVersion(long saplDocumentId, @NonNull String documentValue) {
        var saplDocument = getExistingById(saplDocumentId);

        var parsedDocument = saplParser.parseDocument(documentValue);
        if (parsedDocument.isInvalid()) {
            throw new IllegalArgumentException("document value is invalid (value: %s)".formatted(documentValue));
        }

        var newVersionNumber = saplDocument.getCurrentVersionNumber() + 1;
        var type             = parsedDocument.type();
        var newName          = parsedDocument.name();

        var newSaplDocumentVersion = new SaplDocumentVersion().setSaplDocument(saplDocument)
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
        var saplDocument = getExistingById(saplDocumentId);

        // unpublish other version if published
        if (saplDocument.getPublishedVersion() != null) {
            var saplDocumentToUnpublish = getExistingById(saplDocumentId);

            var publishedVersion = saplDocumentToUnpublish.getPublishedVersion();
            if (publishedVersion != null) {
                // update persisted published documents
                deletePersistedPublishedSaplDocumentsByName(publishedVersion.getName());

                // update persisted document
                saplDocumentToUnpublish.setPublishedVersion(null);
                saplDocumentRepository.save(saplDocumentToUnpublish);

                log.info("unpublish version {} of SAPL document with id {} (name: {})",
                        publishedVersion.getVersionNumber(), saplDocumentId, saplDocumentToUnpublish.getName());
            }
        }

        var saplDocumentVersionToPublish = saplDocument.getVersion(versionToPublish);

        // update persisted published documents
        createPersistedPublishedSaplDocument(saplDocumentVersionToPublish);

        // update persisted document
        saplDocument.setPublishedVersion(saplDocumentVersionToPublish).setLastModified(getCurrentTimestampAsString());
        saplDocumentRepository.save(saplDocument);

        log.info("publish version {} of SAPL document with id {} (name: {})",
                saplDocumentVersionToPublish.getVersionNumber(), saplDocumentId,
                saplDocumentVersionToPublish.getName());

        notifyPolicyChangeListeners();
    }

    @Transactional
    public void unpublishPolicy(long saplDocumentId) {
        var saplDocumentToUnpublish = getExistingById(saplDocumentId);

        var publishedVersion = saplDocumentToUnpublish.getPublishedVersion();
        if (publishedVersion == null) {
            return;
        }

        // update persisted published documents
        deletePersistedPublishedSaplDocumentsByName(publishedVersion.getName());

        // update persisted document
        saplDocumentToUnpublish.setPublishedVersion(null);
        saplDocumentRepository.save(saplDocumentToUnpublish);

        log.info("unpublish version {} of SAPL document with id {} (name: {})", publishedVersion.getVersionNumber(),
                saplDocumentId, saplDocumentToUnpublish.getName());

        notifyPolicyChangeListeners();
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

    private void deletePersistedPublishedSaplDocumentsByName(String name) {
        var publishedDocumentsWithName = publishedSaplDocumentRepository.findByDocumentName(name);
        publishedSaplDocumentRepository.deleteAll(publishedDocumentsWithName);
    }

    private void createPersistedPublishedSaplDocument(SaplDocumentVersion saplDocumentVersion)
            throws PublishedDocumentNameCollisionException {
        var publishedSaplDocument = new PublishedSaplDocument();
        publishedSaplDocument.importSaplDocumentVersion(saplDocumentVersion);

        publishedSaplDocumentRepository.save(publishedSaplDocument);

        // enforce checking constraints in transaction
        try {
            publishedSaplDocumentRepository.findAll();
        } catch (DataIntegrityViolationException ex) {
            throw new PublishedDocumentNameCollisionException(saplDocumentVersion.getName(), ex);
        }
    }

    private void notifyPolicyChangeListeners() {
        for (var listener : policyChangeListeners) {
            try {
                listener.onPoliciesChanged();
            } catch (Exception exception) {
                log.error("Error notifying policy change listener.", exception);
            }
        }
    }
}
