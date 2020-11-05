package io.sapl.server.ce.views;

import java.util.Optional;

import javax.annotation.PostConstruct;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.service.ClientCredentialsService;
import lombok.RequiredArgsConstructor;

/**
 * A Designer generated component for the list-client-credentials template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("list-client-credentials")
@Route(value = ListClientCredentials.ROUTE, layout = MainView.class)
@JsModule("./list-client-credentials.js")
@PageTitle("Client Credentials")
@RequiredArgsConstructor
public class ListClientCredentials extends PolymerTemplate<ListClientCredentials.ListClientCredentialsModel> {
	public static final String ROUTE = "clients";

	private final ClientCredentialsService clientCredentialsService;

	@Id(value = "clientCredentialsGrid")
	private Grid<ClientCredentials> clientCredentialsGrid;

	@Id(value = "currentKeyTextField")
	private TextField currentKeyTextField;

	@Id(value = "currentSecretPasswordField")
	private PasswordField currentSecretTextField;

	@Id(value = "editCurrentClientCredentialsLayout")
	private VerticalLayout editCurrentClientCredentialsLayout;

	@Id(value = "createButton")
	private Button createButton;

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		editCurrentClientCredentialsLayout.setVisible(false);

		createButton.addClickListener((clickEvent) -> {
			clientCredentialsService.createDefault();
			clientCredentialsGrid.getDataProvider().refreshAll();
		});

		initClientCredentialsGrid();
	}

	private void initClientCredentialsGrid() {
		clientCredentialsGrid.addColumn(ClientCredentials::getKey).setHeader("Key");

		clientCredentialsGrid.addSelectionListener(selection -> {
			Optional<ClientCredentials> firstSelectedItemAsOptional = selection.getFirstSelectedItem();
			firstSelectedItemAsOptional.ifPresentOrElse(clientCredentials -> {
				editCurrentClientCredentialsLayout.setVisible(true);

				currentKeyTextField.setValue(clientCredentials.getKey());
				currentSecretTextField.setValue(clientCredentials.getHashedSecret());
			}, () -> {
				editCurrentClientCredentialsLayout.setVisible(false);
			});
		});

		// set data provider
		CallbackDataProvider<ClientCredentials, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return clientCredentialsService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) clientCredentialsService.getAmount());
		clientCredentialsGrid.setDataProvider(dataProvider);
	}

	/**
	 * This model binds properties between ListClientCredentials and
	 * list-client-credentials
	 */
	public interface ListClientCredentialsModel extends TemplateModel {
	}
}
