/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.playground.ui.components;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.compiler.pdp.TimestampedVote;
import lombok.val;

import java.io.Serial;

/**
 * Grid component for displaying authorization decisions in the playground.
 * Shows decision results with columns for the
 * decision itself, obligations, advice, and resource transformations. Uses
 * badges to visually indicate presence of
 * optional fields.
 */
public class DecisionsGrid extends Grid<TimestampedVote> {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a new decisions grid with configured columns. Sets up columns for
     * decision, obligations, advice, and
     * resource, with automatic width adjustment and borderless theme.
     */
    public DecisionsGrid() {
        super();
        setSizeFull();
        addColumn(this::extractDecisionString).setHeader("Decision").setAutoWidth(true);
        addColumn(renderObligationsBadge()).setHeader("Obligations").setAutoWidth(true);
        addColumn(renderAdviceBadge()).setHeader("Advice").setAutoWidth(true);
        addColumn(renderResourceBadge()).setHeader("Resource").setAutoWidth(true);
        addThemeVariants(GridVariant.LUMO_NO_BORDER);
    }

    /*
     * Extracts the decision string from a timestamped vote. Converts the decision
     * enum to its string representation.
     */
    private String extractDecisionString(TimestampedVote timestampedVote) {
        return timestampedVote.vote().authorizationDecision().decision().toString();
    }

    /*
     * Creates renderer for obligations badge column. Shows "Obligations" badge if
     * obligations are present, otherwise
     * shows "-/-".
     */
    private ComponentRenderer<Span, TimestampedVote> renderObligationsBadge() {
        return Badger.badgeRenderer(tv -> !tv.vote().authorizationDecision().obligations().isEmpty(), Badger.PRIMARY,
                Badger.SUCCESS, "Obligations", "-/-");
    }

    /*
     * Creates renderer for advice badge column. Shows "Advice" badge if advice is
     * present, otherwise shows "-/-".
     */
    private ComponentRenderer<Span, TimestampedVote> renderAdviceBadge() {
        return Badger.badgeRenderer(tv -> !tv.vote().authorizationDecision().advice().isEmpty(), Badger.PRIMARY,
                Badger.SUCCESS, "Advice", "-/-");
    }

    /*
     * Creates renderer for resource badge column. Shows "Resource" badge if
     * resource transformation is present,
     * otherwise shows "-/-".
     */
    private ComponentRenderer<Span, TimestampedVote> renderResourceBadge() {
        return Badger.badgeRenderer(tv -> {
            val resource = tv.vote().authorizationDecision().resource();
            return !(resource instanceof UndefinedValue) && !(resource instanceof ErrorValue);
        }, Badger.PRIMARY, Badger.SUCCESS, "Resource", "-/-");
    }
}
