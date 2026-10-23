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
package io.sapl.vaadin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;

/**
 * This class provides functions to create different builder objects: - Single
 * builder (See overloaded "with()" for each different vaadin component, e.g.
 * Button, TextField, etc.) - Multi builder for multi subscriptions (See
 * {@link PepBuilderService#getMultiBuilder()}) - Lifecycle before enter builder
 * (See {@link PepBuilderService#getLifecycleBeforeEnterPepBuilder()}) -
 * Lifecycle before leave builder (See
 * {@link PepBuilderService#getLifecycleBeforeLeavePepBuilder()})
 */
@RequiredArgsConstructor
public class PepBuilderService {

    private final ReactivePolicyDecisionPoint        pdp;
    private final VaadinConstraintEnforcementService vaadinConstraintEnforcementService;

    /**
     * This function creates and returns a Single-Builder for subscriptions with a
     * vaadin component.
     *
     * @param component Component to be linked with the Single-Pep-Builder
     * @return New {@link VaadinPep.VaadinSingleComponentPepBuilder}
     */
    public VaadinPep.VaadinSingleComponentPepBuilder with(Component component) {
        return new VaadinPep.VaadinSingleComponentPepBuilder(pdp, vaadinConstraintEnforcementService, component);
    }

    /**
     * This function creates and returns a Single-Builder for subscriptions with a
     * vaadin button.
     *
     * @param button Button to be linked with the Single-Pep-Builder
     * @return New {@link VaadinPep.VaadinSingleButtonPepBuilder}
     */
    public VaadinPep.VaadinSingleButtonPepBuilder with(Button button) {
        return new VaadinPep.VaadinSingleButtonPepBuilder(pdp, vaadinConstraintEnforcementService, button);
    }

    /**
     * This function creates and returns a Single-Builder for subscriptions with a
     * vaadin text field.
     *
     * @param textField Text field to be linked with the Single-Pep-Builder
     * @return New {@link VaadinPep.VaadinSingleTextFieldPepBuilder}
     */
    public VaadinPep.VaadinSingleTextFieldPepBuilder with(TextField textField) {
        return new VaadinPep.VaadinSingleTextFieldPepBuilder(pdp, vaadinConstraintEnforcementService, textField);
    }

    /**
     * This function creates and returns a Single-Builder for subscriptions with a
     * vaadin checkbox.
     *
     * @param checkbox Checkbox to be linked with the Single-Pep-Builder
     * @return New {@link VaadinPep.VaadinSingleCheckboxPepBuilder}
     */
    public VaadinPep.VaadinSingleCheckboxPepBuilder with(Checkbox checkbox) {
        return new VaadinPep.VaadinSingleCheckboxPepBuilder(pdp, vaadinConstraintEnforcementService, checkbox);
    }

    /**
     * This function creates and returns a Single-Builder for subscriptions with a
     * vaadin span.
     *
     * @param span Span to be linked with the Single-Pep-Builder
     * @return New {@link VaadinPep.VaadinSingleSpanPepBuilder}
     */
    public VaadinPep.VaadinSingleSpanPepBuilder with(Span span) {
        return new VaadinPep.VaadinSingleSpanPepBuilder(pdp, vaadinConstraintEnforcementService, span);
    }

    /**
     * This function creates and returns a {@link MultiBuilder} object.
     *
     * @return New {@link MultiBuilder}
     */
    public MultiBuilder getMultiBuilder() {
        return new MultiBuilder(pdp, vaadinConstraintEnforcementService);
    }

    /**
     * This function creates and returns a builder for subscriptions with the before
     * enter event.
     *
     * @return New {@link VaadinPep.LifecycleBeforeEnterPepBuilder}
     */
    public VaadinPep.LifecycleBeforeEnterPepBuilder getLifecycleBeforeEnterPepBuilder() {
        return new VaadinPep.LifecycleBeforeEnterPepBuilder(pdp, vaadinConstraintEnforcementService);
    }

    /**
     * This function creates and returns a builder for subscriptions with the before
     * leave event.
     *
     * @return New {@link VaadinPep.LifecycleBeforeLeavePepBuilder}
     */
    public VaadinPep.LifecycleBeforeLeavePepBuilder getLifecycleBeforeLeavePepBuilder() {
        return new VaadinPep.LifecycleBeforeLeavePepBuilder(pdp, vaadinConstraintEnforcementService);
    }
}
