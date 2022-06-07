package io.sapl.server.ce.views.publishedpolicies;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;
import io.sapl.server.ce.service.sapldocument.SaplDocumentService;
import io.sapl.server.ce.views.MainView;
import io.sapl.server.ce.views.sapldocument.EditSaplDocumentView;
import io.sapl.server.ce.views.utils.confirm.ConfirmUtils;
import io.sapl.server.ce.views.utils.error.ErrorNotificationUtils;
import io.sapl.vaadin.SaplEditor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Tag("published-policies-view")
@Route(value = PublishedPoliciesView.ROUTE, layout = MainView.class)
@Slf4j
@JsModule("./published-policies-view.js")
@PageTitle("Published Policies")
public class PublishedPoliciesView extends PolymerTemplate<PublishedPoliciesView.PublishedPoliciesViewModel> {
    public static final String ROUTE = "published";

    private final SaplDocumentService saplDocumentService;

    @Id(value = "grid")
    private Grid<PublishedSaplDocument> grid;

    @Id(value = "layoutForSelectedPublishedDocument")
    private VerticalLayout layoutForSelectedPublishedDocument;

    @Id(value = "policyIdTextField")
    private TextField policyIdTextField;

    @Id(value = "publishedVersionTextField")
    private TextField publishedVersionTextField;

    @Id(value = "openEditPageForPolicyButton")
    private Button openEditPageForPolicyButton;

    @Id(value = "saplEditor")
    private SaplEditor saplEditor;

    public PublishedPoliciesView(@NonNull SaplDocumentService saplDocumentService) {
        this.saplDocumentService = saplDocumentService;

        initUI();
    }

    private void initUI() {
        initGrid();

        layoutForSelectedPublishedDocument.setVisible(false);

        saplEditor.setReadOnly(Boolean.TRUE);
        openEditPageForPolicyButton.addClickListener((clickEvent) -> {
            PublishedSaplDocument selected = getSelected();

            String uriToNavigateTo = String.format("%s/%s", EditSaplDocumentView.ROUTE, selected.getSaplDocumentId());
            getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
        });
    }

    private void initGrid() {
        grid.addColumn(PublishedSaplDocument::getDocumentName)
                .setHeader("Name")
                .setSortable(true);
        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        grid.setMultiSort(false);
        grid.addComponentColumn(publishedDocument -> {
            Button unpublishButton = new Button("Unpublish");
            unpublishButton.addClickListener(clickEvent -> {
                ConfirmUtils.letConfirm(
                        String.format("Should the document \"%s\" really be unpublished?", publishedDocument.getDocumentName()),
                        () -> {
                            try {
                                saplDocumentService.unpublishPolicy(publishedDocument.getSaplDocumentId());
                            } catch (Throwable throwable) {
                                getUI().ifPresent((ui) -> ui.access(() ->
                                        ErrorNotificationUtils.show("The document could not be unpublished.")));
                                log.error(String.format(
                                        "The document with id %s could not be unpublished.",
                                        publishedDocument.getSaplDocumentId()), throwable);
                                return;
                            }

                            getUI().ifPresent((ui) -> ui.access(() -> grid.getDataProvider().refreshAll()));
                        }, null);
            });

            HorizontalLayout componentsForEntry = new HorizontalLayout();
            componentsForEntry.add(unpublishButton);

            return componentsForEntry;
        });

        CallbackDataProvider<PublishedSaplDocument, Void> dataProvider = DataProvider.fromCallbacks(query -> {
            Stream<PublishedSaplDocument> stream = saplDocumentService.getPublishedSaplDocuments().stream();

            Optional<Comparator<PublishedSaplDocument>> comparator = query.getSortingComparator();
            if (comparator.isPresent()) {
                stream = stream.sorted(comparator.get());
            }

            return stream
                    .skip(query.getOffset())
                    .limit(query.getLimit());
        }, query -> (int) saplDocumentService.getPublishedAmount());
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.setAllRowsVisible(true);
        grid.setPageSize(25);
        grid.setDataProvider(dataProvider);
        grid.setMultiSort(false);
        grid.addSelectionListener(selection -> {
            Optional<PublishedSaplDocument> selectedItem = selection.getFirstSelectedItem();
            selectedItem.ifPresentOrElse((selectedPublishedDocument) -> {
                layoutForSelectedPublishedDocument.setVisible(true);

                policyIdTextField.setValue(Long.toString(selectedPublishedDocument.getSaplDocumentId()));
                publishedVersionTextField.setValue(Integer.toString(selectedPublishedDocument.getVersion()));
                saplEditor.setDocument(selectedPublishedDocument.getDocument());
            }, () -> {
                layoutForSelectedPublishedDocument.setVisible(false);
            });
        });
    }

    private PublishedSaplDocument getSelected() {
        Optional<PublishedSaplDocument> optionalPersistedPublishedDocument = grid.getSelectedItems().stream().findFirst();
        if (optionalPersistedPublishedDocument.isEmpty()) {
            throw new IllegalStateException("not available if no published document is selected");
        }

        return optionalPersistedPublishedDocument.get();
    }

    public interface PublishedPoliciesViewModel extends TemplateModel {
    }
}
