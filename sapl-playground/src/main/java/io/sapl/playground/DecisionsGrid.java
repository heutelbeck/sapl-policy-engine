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
package io.sapl.playground;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.sapl.api.pdp.TracedDecision;

public class DecisionsGrid extends Grid<TracedDecision> {

    public DecisionsGrid() {
        super();
        setSizeFull();
        addColumn(this::provideDecision).setHeader("Decision").setAutoWidth(true);
        addColumn(renderObligations()).setHeader("Obligations").setAutoWidth(true);
        addColumn(renderAdvice()).setHeader("Advice").setAutoWidth(true);
        addColumn(renderResource()).setHeader("Resource").setAutoWidth(true);
        addThemeVariants(GridVariant.LUMO_NO_BORDER);
    }

    private String provideDecision(TracedDecision decision) {
        return decision.getAuthorizationDecision().getDecision().toString();
    }

    private ComponentRenderer<Span, TracedDecision> renderObligations() {
        return Badger.badgeRenderer(decision -> decision.getAuthorizationDecision().getObligations().isPresent(),
                Badger.PRIMARY, Badger.SUCCESS, "Obligations", "-/-");
    }

    private ComponentRenderer<Span, TracedDecision> renderAdvice() {
        return Badger.badgeRenderer(decision -> decision.getAuthorizationDecision().getAdvice().isPresent(),
                Badger.PRIMARY, Badger.SUCCESS, "Advice", "-/-");
    }

    private ComponentRenderer<Span, TracedDecision> renderResource() {
        return Badger.badgeRenderer(decision -> decision.getAuthorizationDecision().getResource().isPresent(),
                Badger.PRIMARY, Badger.SUCCESS, "Resource", "-/-");
    }

}
