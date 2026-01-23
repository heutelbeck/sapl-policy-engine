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
package io.sapl.server.ce.ui.views.pdpconfig;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.server.ce.model.pdpconfiguration.CombiningAlgorithmService;
import io.sapl.server.ce.model.pdpconfiguration.DuplicatedVariableNameException;
import io.sapl.server.ce.model.pdpconfiguration.InvalidVariableNameException;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.model.pdpconfiguration.VariablesService;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorNotificationUtils;
import io.sapl.server.ce.ui.views.MainLayout;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;

import java.io.Serial;
import java.util.List;

@Slf4j
@RolesAllowed("ADMIN")
@PageTitle("PDP Configuration")
@Route(value = PDPConfigView.ROUTE, layout = MainLayout.class)
@Conditional(SetupFinishedCondition.class)
public class PDPConfigView extends VerticalLayout {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public static final String ROUTE = "pdp-security";

    private static final String LABEL_COMBINING_ALGORITHM = "Combining Algorithm:";
    private static final String LABEL_ERRORS_SEPARATOR    = "errors";
    private static final String LABEL_OR                  = "or";

    private static final List<VotingMode> VOTING_MODES = List.of(VotingMode.PRIORITY_DENY, VotingMode.PRIORITY_PERMIT,
            VotingMode.UNANIMOUS, VotingMode.UNANIMOUS_STRICT, VotingMode.UNIQUE);

    private transient CombiningAlgorithmService combiningAlgorithmService;
    private transient VariablesService          variablesService;

    private final ComboBox<VotingMode>      votingModeCombo      = new ComboBox<>();
    private final ComboBox<DefaultDecision> defaultDecisionCombo = new ComboBox<>();
    private final ComboBox<ErrorHandling>   errorHandlingCombo   = new ComboBox<>();
    private final Grid<Variable>            variablesGrid        = new Grid<>();
    private final Button                    createVariableButton = new Button("New Variable");

    private boolean isIgnoringNextVotingModeChange;
    private boolean isIgnoringNextDefaultDecisionChange;
    private boolean isIgnoringNextErrorHandlingChange;

    public PDPConfigView(VariablesService variablesService, CombiningAlgorithmService combiningAlgorithmService) {
        this.combiningAlgorithmService = combiningAlgorithmService;
        this.variablesService          = variablesService;

        var algorithmLayout = createCombiningAlgorithmLayout();

        add(algorithmLayout, createVariableButton, variablesGrid);

        initUiForCombiningAlgorithm();
        initUiForVariables();
    }

    private HorizontalLayout createCombiningAlgorithmLayout() {
        var algorithmLabel = new Span(LABEL_COMBINING_ALGORITHM);
        var orLabel        = new Span(LABEL_OR);
        var errorsLabel    = new Span(LABEL_ERRORS_SEPARATOR);

        var layout = new HorizontalLayout(algorithmLabel, votingModeCombo, orLabel, defaultDecisionCombo, errorsLabel,
                errorHandlingCombo);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        layout.addClassNames(LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Padding.SMALL);

        return layout;
    }

    private void initUiForCombiningAlgorithm() {
        votingModeCombo.setItems(VOTING_MODES);
        votingModeCombo.setItemLabelGenerator(PDPConfigView::formatVotingMode);
        votingModeCombo.setValue(combiningAlgorithmService.getVotingMode());
        votingModeCombo.addValueChangeListener(event -> {
            if (isIgnoringNextVotingModeChange) {
                isIgnoringNextVotingModeChange = false;
                return;
            }
            var newValue = event.getValue();
            ConfirmUtils.letConfirm("Change Voting Mode",
                    "Changing the voting mode affects how policy decisions are combined.\n\nConfirm?",
                    () -> combiningAlgorithmService.setVotingMode(newValue), () -> {
                        isIgnoringNextVotingModeChange = true;
                        votingModeCombo.setValue(event.getOldValue());
                    });
        });

        defaultDecisionCombo.setItems(DefaultDecision.values());
        defaultDecisionCombo.setItemLabelGenerator(PDPConfigView::formatDefaultDecision);
        defaultDecisionCombo.setValue(combiningAlgorithmService.getDefaultDecision());
        defaultDecisionCombo.addValueChangeListener(event -> {
            if (isIgnoringNextDefaultDecisionChange) {
                isIgnoringNextDefaultDecisionChange = false;
                return;
            }
            var newValue = event.getValue();
            ConfirmUtils.letConfirm("Change Default Decision",
                    "Changing the default decision affects what happens when no policy applies.\n\nConfirm?",
                    () -> combiningAlgorithmService.setDefaultDecision(newValue), () -> {
                        isIgnoringNextDefaultDecisionChange = true;
                        defaultDecisionCombo.setValue(event.getOldValue());
                    });
        });

        errorHandlingCombo.setItems(ErrorHandling.values());
        errorHandlingCombo.setItemLabelGenerator(PDPConfigView::formatErrorHandling);
        errorHandlingCombo.setValue(combiningAlgorithmService.getErrorHandling());
        errorHandlingCombo.addValueChangeListener(event -> {
            if (isIgnoringNextErrorHandlingChange) {
                isIgnoringNextErrorHandlingChange = false;
                return;
            }
            var newValue = event.getValue();
            ConfirmUtils.letConfirm("Change Error Handling",
                    "Changing error handling affects how errors are treated during evaluation.\n\nConfirm?",
                    () -> combiningAlgorithmService.setErrorHandling(newValue), () -> {
                        isIgnoringNextErrorHandlingChange = true;
                        errorHandlingCombo.setValue(event.getOldValue());
                    });
        });
    }

    private static String formatVotingMode(VotingMode mode) {
        return mode.name().replace('_', ' ').toLowerCase();
    }

    private static String formatDefaultDecision(DefaultDecision decision) {
        return decision.name().toLowerCase();
    }

    private static String formatErrorHandling(ErrorHandling handling) {
        return handling.name().toLowerCase();
    }

    private void initUiForVariables() {
        createVariableButton.addClickListener(clickEvent -> showDialogForVariableCreation());

        initVariablesGrid();
    }

    private void showDialogForVariableCreation() {
        CreateVariable dialogContent = new CreateVariable();

        Dialog createDialog = new Dialog(dialogContent);
        createDialog.setWidth("600px");
        createDialog.setModal(true);
        createDialog.setCloseOnEsc(false);
        createDialog.setCloseOnOutsideClick(false);

        dialogContent.setUserConfirmedListener(isConfirmed -> {
            if (isConfirmed) {
                String name = dialogContent.getNameOfVariableToCreate();
                try {
                    variablesService.create(name);
                } catch (InvalidVariableNameException ex) {
                    log.error("cannot create variable due to invalid name", ex);
                    ErrorNotificationUtils.show("The name is invalid (min length: %d, max length: %d)."
                            .formatted(VariablesService.MIN_NAME_LENGTH, VariablesService.MAX_NAME_LENGTH));
                    return;
                } catch (DuplicatedVariableNameException ex) {
                    log.error("cannot create variable due to duplicated name", ex);
                    ErrorNotificationUtils.show("The name is already used by another variable.");
                    return;
                }

                // reload grid after creation
                variablesGrid.getDataProvider().refreshAll();
            }

            createDialog.close();
        });

        createDialog.open();
    }

    private void initVariablesGrid() {
        // add columns
        variablesGrid.addColumn(Variable::getName).setHeader("Name");
        variablesGrid.addColumn(Variable::getJsonValue).setHeader("JSON Value");
        variablesGrid.addComponentColumn(variable -> {
            Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
            editButton.addClickListener(clickEvent -> {
                String uriToNavigateTo = "%s/%d".formatted(EditVariableView.ROUTE, variable.getId());
                editButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
            });
            editButton.setThemeName("primary");

            Button deleteButton = new Button("Delete", VaadinIcon.FILE_REMOVE.create());
            deleteButton.addClickListener(clickEvent -> {
                variablesService.delete(variable.getId());

                // trigger refreshing variable grid
                variablesGrid.getDataProvider().refreshAll();
            });
            deleteButton.setThemeName("primary");

            var componentsForEntry = new HorizontalLayout();
            componentsForEntry.add(editButton);
            componentsForEntry.add(deleteButton);

            return componentsForEntry;
        });

        // set data provider
        CallbackDataProvider<Variable, Void> dataProvider = DataProvider.fromCallbacks(query -> {
            int offset = query.getOffset();
            int limit  = query.getLimit();

            return variablesService.getAll().stream().skip(offset).limit(limit);
        }, query -> (int) variablesService.getAmount());
        variablesGrid.setItems(dataProvider);

        variablesGrid.setAllRowsVisible(true);
    }

}
