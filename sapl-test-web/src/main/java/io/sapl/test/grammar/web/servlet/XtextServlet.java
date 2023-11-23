/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.grammar.web.servlet;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Injector;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.web.server.IServiceContext;
import org.eclipse.xtext.web.server.IServiceResult;
import org.eclipse.xtext.web.server.IUnwrappableServiceResult;
import org.eclipse.xtext.web.server.InvalidRequestException;
import org.eclipse.xtext.web.server.XtextServiceDispatcher;
import org.eclipse.xtext.xbase.lib.Exceptions;

/**
 * An HTTP servlet for publishing the Xtext services. Include this into your web
 * server by creating a subclass that executes the standalone setups of your
 * languages in its {@link #init()} method:
 * 
 * <pre>
 * &#64;WebServlet(name = "Xtext Services", urlPatterns = "/xtext-service/*")
 * class MyXtextServlet extends XtextServlet {
 * 	override init() {
 * 		super.init();
 * 		MyDslWebSetup.doSetup();
 * 	}
 * }
 * </pre>
 * 
 * Use the {@code WebServlet} annotation to register your servlet. The default
 * URL pattern for Xtext services is {@code "/xtext-service/*"}.
 */
@Slf4j
public class XtextServlet extends HttpServlet {

	private static final IResourceServiceProvider.Registry serviceProviderRegistry = IResourceServiceProvider.Registry.INSTANCE;
	private static final Gson gson = new Gson();
	private static final String INVALID_REQUEST_STRING = "Invalid request (";

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			super.service(req, resp);
		} catch (InvalidRequestException.ResourceNotFoundException exception) {
			log.trace(INVALID_REQUEST_STRING + req.getRequestURI() + "): " + exception.getMessage());
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, exception.getMessage());
		} catch (InvalidRequestException.InvalidDocumentStateException exception) {
			log.trace(INVALID_REQUEST_STRING + req.getRequestURI() + "): " + exception.getMessage());
			resp.sendError(HttpServletResponse.SC_CONFLICT, exception.getMessage());
		} catch (InvalidRequestException.PermissionDeniedException exception) {
			log.trace(INVALID_REQUEST_STRING + req.getRequestURI() + "): " + exception.getMessage());
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, exception.getMessage());
		} catch (InvalidRequestException exception) {
			log.trace(INVALID_REQUEST_STRING + req.getRequestURI() + "): " + exception.getMessage());
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		XtextServiceDispatcher.ServiceDescriptor service = getService(req);
		if (!service.isHasConflict() && (service.isHasSideEffects() || hasTextInput(service))) {
			super.doGet(req, resp);
		} else {
			doService(service, resp);
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		XtextServiceDispatcher.ServiceDescriptor service = getService(req);
		String type = service.getContext().getParameter(IServiceContext.SERVICE_TYPE);
		if (!service.isHasConflict() && !Objects.equal(type, "update")) {
			super.doPut(req, resp);
		} else {
			doService(service, resp);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		XtextServiceDispatcher.ServiceDescriptor service = getService(req);
		String type = service.getContext().getParameter(IServiceContext.SERVICE_TYPE);
		if (!service.isHasConflict()
				&& (!service.isHasSideEffects() && !hasTextInput(service) || Objects.equal(type, "update"))) {
			super.doPost(req, resp);
		} else {
			doService(service, resp);
		}
	}

	protected boolean hasTextInput(XtextServiceDispatcher.ServiceDescriptor service) {
		Set<String> parameterKeys = service.getContext().getParameterKeys();
		return parameterKeys.contains("fullText") || parameterKeys.contains("deltaText");
	}

	/**
	 * Retrieve the service metadata for the given request. This involves resolving
	 * the Guice injector for the respective language, querying the
	 * {@link XtextServiceDispatcher}, and checking the permission to invoke the
	 * service.
	 */
	protected XtextServiceDispatcher.ServiceDescriptor getService(HttpServletRequest request)
			throws InvalidRequestException {
		HttpServiceContext serviceContext = new HttpServiceContext(request);
		Injector injector = getInjector(serviceContext);
		XtextServiceDispatcher serviceDispatcher = injector.getInstance(XtextServiceDispatcher.class);
		return serviceDispatcher.getService(serviceContext);
	}

	/**
	 * Invoke the service function of the given service descriptor and write its
	 * result to the servlet response in Json format. An exception is made for
	 * {@link IUnwrappableServiceResult}: here the document itself is written into
	 * the response instead of wrapping it into a Json object.
	 */
	protected void doService(XtextServiceDispatcher.ServiceDescriptor service, HttpServletResponse response) {
		try {
			IServiceResult result = service.getService().apply();
			response.setStatus(HttpServletResponse.SC_OK);
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Cache-Control", "no-cache");
			if (result instanceof IUnwrappableServiceResult unwrapResult
					&& ((IUnwrappableServiceResult) result).getContent() != null) {
				String contentType;
				if (unwrapResult.getContentType() != null) {
					contentType = unwrapResult.getContentType();
				} else {
					contentType = "text/plain";
				}
				response.setContentType(contentType);
				response.getWriter().write(unwrapResult.getContent());
			} else {
				response.setContentType("text/x-json");
				gson.toJson(result, response.getWriter());
			}
		} catch (IOException e) {
			throw Exceptions.sneakyThrow(e);
		}
	}
	
	/**
	 * Resolve the Guice injector for the language associated with the given
	 * context.
	 */
	protected Injector getInjector(HttpServiceContext serviceContext)
			throws InvalidRequestException.UnknownLanguageException {
		IResourceServiceProvider resourceServiceProvider;
		String parameter = serviceContext.getParameter("resource");
		if (parameter == null) {
			parameter = "";
		}
		URI emfURI = URI.createURI(parameter);
		String contentType = serviceContext.getParameter("contentType");
		if (Strings.isNullOrEmpty(contentType)) {
			resourceServiceProvider = serviceProviderRegistry.getResourceServiceProvider(emfURI);
			if (resourceServiceProvider == null) {
				if (emfURI.toString().isEmpty()) {
					throw new InvalidRequestException.UnknownLanguageException(
							"Unable to identify the Xtext language: missing parameter 'resource' or 'contentType'.");
				} else {
					throw new InvalidRequestException.UnknownLanguageException(
							"Unable to identify the Xtext language for resource " + emfURI + ".");
				}
			}
		} else {
			resourceServiceProvider = serviceProviderRegistry.getResourceServiceProvider(emfURI, contentType);
			if (resourceServiceProvider == null) {
				throw new InvalidRequestException.UnknownLanguageException(
						"Unable to identify the Xtext language for contentType " + contentType + ".");
			}
		}
		return resourceServiceProvider.get(Injector.class);
	}
}