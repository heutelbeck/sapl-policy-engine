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
package io.sapl.grammar.web;

import javax.servlet.annotation.WebServlet;

import org.eclipse.xtext.util.DisposableRegistry;
import org.eclipse.xtext.web.servlet.XtextServlet;

import com.google.inject.Injector;

import lombok.SneakyThrows;

/**
 * Deploy this class into a servlet container to enable DSL-specific services.
 */
@WebServlet(name = "XtextServices", urlPatterns = "/xtext-service/*")
public class SAPLServlet extends XtextServlet {

	private static final long serialVersionUID = 1L;

	private transient DisposableRegistry disposableRegistry;

	@Override
	@SneakyThrows
	public void init() {
		super.init();
		final Injector injector = new SAPLWebSetup().createInjectorAndDoEMFRegistration();
		disposableRegistry = injector.<DisposableRegistry>getInstance(DisposableRegistry.class);
	}

	@Override
	public void destroy() {
		if (disposableRegistry != null) {
			disposableRegistry.dispose();
			disposableRegistry = null;
		}
		super.destroy();
	}

}
