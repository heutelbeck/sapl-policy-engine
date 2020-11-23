/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views;

import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Login")
@Route(value = LoginView.ROUTE)
public class LoginView extends VerticalLayout {
	public static final String ROUTE = "login";

	public LoginView() {
		LoginOverlay loginOverlay = new LoginOverlay();

		loginOverlay.setAction("login");
		loginOverlay.setOpened(true);
		loginOverlay.setTitle("SAPL PDP-Server CE");
		loginOverlay.setDescription("");
		loginOverlay.setForgotPasswordButtonVisible(false);

		getElement().appendChild(loginOverlay.getElement());
	}
}
