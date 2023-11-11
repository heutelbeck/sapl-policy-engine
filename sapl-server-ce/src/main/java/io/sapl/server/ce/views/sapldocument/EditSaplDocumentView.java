/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views.sapldocument;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentVersion;
import io.sapl.server.ce.service.sapldocument.PublishedDocumentNameCollisionException;
import io.sapl.server.ce.service.sapldocument.SaplDocumentService;
import io.sapl.server.ce.views.MainView;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import io.sapl.vaadin.Issue;
import io.sapl.vaadin.SaplEditor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * View to edit a {@link SaplDocument}.
 */
@Tag("edit-sapl-document")
@Route(value = EditSaplDocumentView.ROUTE, layout = MainView.class)
@Slf4j
@JsModule("./edit-sapl-document.js")
@PageTitle("Edit SAPL document")
@RequiredArgsConstructor
public class EditSaplDocumentView extends PolymerTemplate<EditSaplDocumentView.EditSaplDocumentModel>
		implements HasUrlParameter<Long> {
	public static final String ROUTE = "documents";

	private static final String NEW_VERSION_SELECTION_ENTRY = "New Version";

	private final SaplDocumentService saplDocumentService;

	private SaplDocumentVersion selectedSaplDocumentVersion;
	private boolean isSelectedVersionRestoredViaEditedDocument;

	@Id(value = "policyIdTextField")
	private TextField policyIdTextField;

	@Id(value = "currentVersionTextField")
	private TextField currentVersionTextField;

	@Id(value = "lastModifiedTextField")
	private TextField lastModifiedTextField;

	@Id(value = "publishedVersionTextField")
	private TextField publishedVersionTextField;

	@Id(value = "publishedNameTextField")
	private TextField publishedNameTextField;

	@Id(value = "saplEditor")
	private SaplEditor saplEditor;

	@Id(value = "versionSelectionComboBox")
	private ComboBox<String> versionSelectionComboBox;

	@Id(value = "saveVersionButton")
	private Button saveVersionButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	@Id(value = "publishCurrentVersionButton")
	private Button publishCurrentVersionButton;

	@Id(value = "unpublishButton")
	private Button unpublishButton;

	/**
	 * The {@link SaplDocument} to edit.
	 */
	private SaplDocument saplDocument;

	private long saplDocumentId;
	private boolean isFirstDocumentValueValidation;

	@Override
	public void setParameter(BeforeEvent event, Long parameter) {
		saplDocumentId = parameter;

		reloadSaplDocument();
		addListener();

		addAttachListener((__) -> {
			if (saplDocument == null) {
				log.warn("SAPL document with id {} is not existing, redirect to list view", parameter);
				getUI().ifPresent(ui -> ui.navigate(SaplDocumentsView.ROUTE));
			}
		});
	}

	private void reloadSaplDocument() {
		Optional<SaplDocument> optionalSaplDocument = saplDocumentService.getById(saplDocumentId);
		if (optionalSaplDocument.isEmpty()) {
			// Vaadin UI object is not available yet, redirect to list view via attach listener
			return;
		}

		saplDocument = optionalSaplDocument.get();
		setUI();

		saveVersionButton.setEnabled(false);

		isFirstDocumentValueValidation = true;
	}

	private void addListener() {
		versionSelectionComboBox.addValueChangeListener(changedEvent -> {
			updateSaplEditorBasedOnVersionSelection();
		});

		saplEditor.addValidationFinishedListener(validationFinishedEvent -> {
			if (isFirstDocumentValueValidation) {
				isFirstDocumentValueValidation = false;
				return;
			}

			String document = saplEditor.getDocument();

			if (selectedSaplDocumentVersion != null && selectedSaplDocumentVersion.getValue().equals(document)) {
				isSelectedVersionRestoredViaEditedDocument = true;
				versionSelectionComboBox.setValue(Integer.toString(selectedSaplDocumentVersion.getVersionNumber()));
				return;
			}

			versionSelectionComboBox.setValue(NEW_VERSION_SELECTION_ENTRY);

			Issue[] issues = validationFinishedEvent.getIssues();
			boolean areNoIssuesAvailable = issues.length == 0
					&& document.length() <= SaplDocumentVersion.MAX_DOCUMENT_SIZE;
			saveVersionButton.setEnabled(areNoIssuesAvailable);
		});

		saveVersionButton.addClickListener(clickEvent -> {
			String currentDocumentValue = saplEditor.getDocument();
			saplDocumentService.createVersion(saplDocumentId, currentDocumentValue);

			reloadSaplDocument();
		});

		cancelButton.addClickListener(clickEvent -> {
			cancelButton.getUI().ifPresent(ui -> ui.navigate(SaplDocumentsView.ROUTE));
		});

		publishCurrentVersionButton.addClickListener(clickEvent -> {
			Optional<Integer> selectedVersionNumberAsOptional = getSelectedVersionNumber();
			if (selectedVersionNumberAsOptional.isEmpty()) {
				return;
			}

			try {
				saplDocumentService.publishPolicyVersion(saplDocumentId, selectedVersionNumberAsOptional.get());
			} catch (PublishedDocumentNameCollisionException ex) {
				ErrorNotificationUtils.show(ex.getMessage());
				return;
			}

			reloadSaplDocument();
		});

		unpublishButton.addClickListener(clickEvent -> {
			saplDocumentService.unpublishPolicy(saplDocumentId);
			reloadSaplDocument();
		});
	}

	/**
	 * Imports the previously set instance of {@link SaplDocument} to the UI.
	 */
	private void setUI() {
		policyIdTextField.setValue(saplDocument.getId().toString());
		lastModifiedTextField.setValue(saplDocument.getLastModified());
		currentVersionTextField.setValue(Integer.toString(saplDocument.getCurrentVersionNumber()));

		Collection<String> availableVersions = getAvailableVersions();
		versionSelectionComboBox.setItems(availableVersions);
		versionSelectionComboBox.setValue(Iterables.getLast(availableVersions));

		SaplDocumentVersion currentVersion = saplDocument.getCurrentVersion();
		saplEditor.setDocument(currentVersion.getValue());
		selectedSaplDocumentVersion = currentVersion;

		setUiForPublishing();
	}

	private void setUiForPublishing() {
		SaplDocumentVersion publishedVersion = saplDocument.getPublishedVersion();
		boolean isPublishedVersionExisting = publishedVersion != null;

		String publishedVersionAsString;
		String publishedNameAsString;

		if (isPublishedVersionExisting) {
			String publishedName = publishedVersion.getName();

			publishedVersionAsString = Integer.toString(publishedVersion.getVersionNumber());
			publishedNameAsString = publishedName;

			Optional<Integer> selectedVersionNumber = getSelectedVersionNumber();
			if (selectedVersionNumber.isPresent()) {
				SaplDocumentVersion selectedVersion = saplDocument.getVersion(selectedVersionNumber.get());

				boolean isSelectedVersionPublished = publishedVersion.getVersionNumber() == selectedVersion
						.getVersionNumber();
				publishCurrentVersionButton.setEnabled(!isSelectedVersionPublished);
			}
		} else {
			publishCurrentVersionButton.setEnabled(true);

			publishedVersionAsString = "-";
			publishedNameAsString = "-";
		}

		publishedVersionTextField.setValue(publishedVersionAsString);
		publishedNameTextField.setValue(publishedNameAsString);

		unpublishButton.setEnabled(isPublishedVersionExisting);
	}

	private Collection<String> getAvailableVersions() {
		// @formatter:off
		return saplDocument.getVersions().stream()
				.map(saplDocumentVersion -> Integer.toString(saplDocumentVersion.getVersionNumber()))
				.collect(Collectors.toList());
		// @formatter:on
	}

	private void updateSaplEditorBasedOnVersionSelection() {
		Optional<Integer> selectedVersionNumberAsOptional = getSelectedVersionNumber();
		if (!selectedVersionNumberAsOptional.isPresent()) {
			publishCurrentVersionButton.setEnabled(false);
			unpublishButton.setEnabled(false);
			return;
		}

		selectedSaplDocumentVersion = saplDocument.getVersion(selectedVersionNumberAsOptional.get());

		if (isSelectedVersionRestoredViaEditedDocument) {
			/*
			 * do not update content of sapl editor if change is initiated via restored document after
			 * editing (do not reset cursor position of editor)
			 */
			isSelectedVersionRestoredViaEditedDocument = false;
		} else {
			saplEditor.setDocument(selectedSaplDocumentVersion.getValue());
			isFirstDocumentValueValidation = true;
		}

		saveVersionButton.setEnabled(false);

		setUiForPublishing();
	}

	/**
	 * Gets the currently selected version number.
	 * 
	 * @return the version number as {@link Optional} (empty if no explicit version
	 *         is selected)
	 */
	private Optional<Integer> getSelectedVersionNumber() {
		String selectedVersionAsString = versionSelectionComboBox.getValue();
		if (selectedVersionAsString == null || NEW_VERSION_SELECTION_ENTRY.equals(selectedVersionAsString)) {
			return Optional.empty();
		}

		return Optional.of(Integer.valueOf(selectedVersionAsString));
	}

	/**
	 * This model binds properties between CreateSaplDocument and
	 * create-sapl-document
	 */
	public interface EditSaplDocumentModel extends TemplateModel {
	}
}
