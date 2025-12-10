/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.ui.views.setup;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.ApplicationConfigService;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.io.Serial;

@AnonymousAllowed
@PageTitle("HTTP Endpoint Setup")
@Route(value = HttpEndpointSetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class HttpEndpointSetupView extends EndpointSetupView {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public static final String ROUTE = "/setup/http";

    public HttpEndpointSetupView(ApplicationConfigService applicationConfigService,
            HttpServletRequest httpServletRequest) {
        super(applicationConfigService, applicationConfigService.getHttpEndpoint(), httpServletRequest);
    }

    @Override
    void persistConfig() throws IOException {
        applicationConfigService.persistHttpEndpointConfig();
    }
}
