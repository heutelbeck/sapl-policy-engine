package io.sapl.server.ce.views;

import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Login")
@Route(value = LoginView.ROUTE)
public class LoginView extends VerticalLayout {
	public static final String ROUTE = "login";

	private final LoginOverlay loginOverlay = new LoginOverlay();

	public LoginView() {
		this.loginOverlay.setAction("login");
		this.loginOverlay.setOpened(true);
		this.loginOverlay.setTitle("SAPL PDP-Server Community Edition (CE)");
		this.loginOverlay.setDescription("");
		this.getElement().appendChild(loginOverlay.getElement());
	}
}
