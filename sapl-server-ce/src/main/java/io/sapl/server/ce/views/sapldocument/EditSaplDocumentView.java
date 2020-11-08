package io.sapl.server.ce.views.sapldocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

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
import io.sapl.server.ce.utils.SaplDocumentUtils;
import io.sapl.server.ce.views.MainView;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import io.sapl.vaadin.Issue;
import io.sapl.vaadin.SaplEditor;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * View to edit a {@link SaplDocument}.
 */
@NoArgsConstructor
@Tag("edit-sapl-document")
@Route(value = EditSaplDocumentView.ROUTE, layout = MainView.class)
@JsModule("./edit-sapl-document.js")
@PageTitle("Edit SAPL document")
public class EditSaplDocumentView extends PolymerTemplate<EditSaplDocumentView.EditSaplDocumentModel>
		implements HasUrlParameter<Long> {
	public static final String ROUTE = "documents";

	private static final String NEW_VERSION_SELECTION_ENTRY = "New Version";

	@Autowired
	private SaplDocumentService saplDocumentService;

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
	@NonNull
	private SaplDocument saplDocument;

	private long saplDocumentId;
	private boolean isDocumentValueEdited;
	private boolean isFirstDocumentValueValidation;

	@Override
	public void setParameter(BeforeEvent event, Long parameter) {
		this.saplDocumentId = parameter;

		this.reloadSaplDocument();
		this.addListener();
	}

	private void reloadSaplDocument() {
		SaplDocument saplDocument = this.saplDocumentService.getById(this.saplDocumentId);

		this.saplDocument = saplDocument;
		this.setUI();

		this.saveVersionButton.setEnabled(false);

		this.isDocumentValueEdited = false;
		this.isFirstDocumentValueValidation = true;
	}

	private void addListener() {
		this.versionSelectionComboBox.addValueChangeListener(changedEvent -> {
			this.updateSaplEditorBasedOnVersionSelection();
		});

		this.saplEditor.addValidationFinishedListener(validationFinishedEvent -> {
			if (this.isFirstDocumentValueValidation) {
				this.isFirstDocumentValueValidation = false;
				return;
			}

			if (!this.isDocumentValueEdited) {
				this.versionSelectionComboBox.setValue(NEW_VERSION_SELECTION_ENTRY);

				this.isDocumentValueEdited = true;
			}

			Issue[] issues = validationFinishedEvent.getIssues();

			boolean areNoIssuesAvailable = issues.length == 0;
			this.saveVersionButton.setEnabled(areNoIssuesAvailable);
		});

		this.saveVersionButton.addClickListener(clickEvent -> {
			String currentDocumentValue = this.saplEditor.getDocument();
			this.saplDocumentService.createVersion(this.saplDocumentId, currentDocumentValue);

			this.reloadSaplDocument();
		});

		this.cancelButton.addClickListener(clickEvent -> {
			this.cancelButton.getUI().ifPresent(ui -> ui.navigate(SaplDocumentsView.ROUTE));
		});

		this.publishCurrentVersionButton.addClickListener(clickEvent -> {
			Optional<Integer> selectedVersionNumberAsOptional = this.getSelectedVersionNumber();
			if (!selectedVersionNumberAsOptional.isPresent()) {
				return;
			}

			try {
				this.saplDocumentService.publishVersion(this.saplDocumentId, selectedVersionNumberAsOptional.get());
			} catch (PublishedDocumentNameCollisionException ex) {
				ErrorNotificationUtils.show(ex.getMessage());
				return;
			}

			this.reloadSaplDocument();
		});

		this.unpublishButton.addClickListener(clickEvent -> {
			this.saplDocumentService.unpublishVersion(this.saplDocumentId);
			this.reloadSaplDocument();
		});
	}

	/**
	 * Imports the previously set instance of {@link SaplDocument} to the UI.
	 */
	private void setUI() {
		this.policyIdTextField.setValue(this.saplDocument.getId().toString());
		this.lastModifiedTextField.setValue(this.saplDocument.getLastModified());
		this.currentVersionTextField.setValue(Integer.valueOf(this.saplDocument.getCurrentVersionNumber()).toString());

		Collection<String> availableVersions = this.getAvailableVersions();
		this.versionSelectionComboBox.setItems(availableVersions);
		this.versionSelectionComboBox.setValue(Iterables.getLast(availableVersions));

		SaplDocumentVersion currentVersion = this.saplDocument.getCurrentVersion();
		this.saplEditor.setDocument(currentVersion.getValue());

		this.setUiForPublishing();
	}

	private void setUiForPublishing() {
		SaplDocumentVersion publishedVersion = this.saplDocument.getPublishedVersion();
		boolean isPublishedVersionExisting = publishedVersion != null;

		if (isPublishedVersionExisting) {
			String publishedName = SaplDocumentUtils.getNameFromDocumentValue(publishedVersion.getValue());

			this.publishedVersionTextField.setValue(Integer.valueOf(publishedVersion.getVersionNumber()).toString());
			this.publishedNameTextField.setValue(publishedName);

			Optional<Integer> selectedVersionNumber = this.getSelectedVersionNumber();
			if (selectedVersionNumber.isPresent()) {
				SaplDocumentVersion selectedVersion = this.saplDocument.getVersion(selectedVersionNumber.get());

				boolean isSelectedVersionPublished = publishedVersion.getVersionNumber() == selectedVersion
						.getVersionNumber();
				this.publishCurrentVersionButton.setVisible(!isSelectedVersionPublished);
			}
		} else {
			this.publishCurrentVersionButton.setVisible(true);
		}

		this.publishedVersionTextField.setVisible(isPublishedVersionExisting);
		this.publishedNameTextField.setVisible(isPublishedVersionExisting);

		this.unpublishButton.setVisible(isPublishedVersionExisting);
	}

	private Collection<String> getAvailableVersions() {
		List<String> result = new ArrayList<String>();
		for (SaplDocumentVersion saplDocumentVersion : this.saplDocument.getVersions()) {
			String versionAsString = Integer.valueOf(saplDocumentVersion.getVersionNumber()).toString();
			result.add(versionAsString);
		}

		return result;
	}

	private void updateSaplEditorBasedOnVersionSelection() {
		Optional<Integer> selectedVersionNumberAsOptional = this.getSelectedVersionNumber();
		if (!selectedVersionNumberAsOptional.isPresent()) {
			this.publishCurrentVersionButton.setVisible(false);
			this.unpublishButton.setVisible(false);
			return;
		}

		SaplDocumentVersion saplDocumentVersion = this.saplDocument.getVersion(selectedVersionNumberAsOptional.get());

		this.saplEditor.setDocument(saplDocumentVersion.getValue());

		this.isDocumentValueEdited = false;
		this.isFirstDocumentValueValidation = true;
		this.saveVersionButton.setEnabled(false);

		this.setUiForPublishing();
	}

	/**
	 * Gets the currently selected version number.
	 * 
	 * @return the version number as {@link Optional} (empty if no explicit version
	 *         is selected)
	 */
	private Optional<Integer> getSelectedVersionNumber() {
		String selectedVersionAsString = this.versionSelectionComboBox.getValue();
		if (selectedVersionAsString == null || selectedVersionAsString.equals(NEW_VERSION_SELECTION_ENTRY)) {
			return Optional.empty();
		}

		return Optional.of(Integer.parseInt(selectedVersionAsString));
	}

	/**
	 * This model binds properties between CreateSaplDocument and
	 * create-sapl-document
	 */
	public interface EditSaplDocumentModel extends TemplateModel {
	}
}
