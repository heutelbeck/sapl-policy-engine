package io.sapl.server.ce.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

import lombok.AllArgsConstructor;
import lombok.Data;
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

	private final List<ClientCredentials> dummies = new ArrayList<ClientCredentials>();

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		this.editCurrentClientCredentialsLayout.setVisible(false);

		this.createButton.addClickListener((clickEvent) -> {
			this.dummies.add(new ClientCredentials(UUID.randomUUID().toString(), "defaultsecret"));
			this.clientCredentialsGrid.getDataProvider().refreshAll();
		});

		initClientCredentialsGrid();
	}

	private void initClientCredentialsGrid() {
		this.clientCredentialsGrid.addColumn(ClientCredentials::getKey).setHeader("Key");

		this.clientCredentialsGrid.addSelectionListener(selection -> {
			Optional<ClientCredentials> firstSelectedItemAsOptional = selection.getFirstSelectedItem();
			firstSelectedItemAsOptional.ifPresentOrElse(clientCredentials -> {
				this.editCurrentClientCredentialsLayout.setVisible(true);

				currentKeyTextField.setValue(clientCredentials.getKey());
				currentSecretTextField.setValue(clientCredentials.getSecret());
			}, () -> {
				this.editCurrentClientCredentialsLayout.setVisible(false);
			});
		});

		// set data provider
		CallbackDataProvider<ClientCredentials, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return dummies.stream().skip(offset).limit(limit);
		}, query -> (int) dummies.size());
		this.clientCredentialsGrid.setDataProvider(dataProvider);
	}

	@Data
	@AllArgsConstructor
	public class ClientCredentials {
		private String key;
		private String secret;
	}

	/**
	 * This model binds properties between ListClientCredentials and
	 * list-client-credentials
	 */
	public interface ListClientCredentialsModel extends TemplateModel {
	}
}
