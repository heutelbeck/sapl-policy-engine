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
package io.sapl.grammar.ui.highlighting;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.xtext.ui.editor.syntaxcoloring.DefaultHighlightingConfiguration;
import org.eclipse.xtext.ui.editor.syntaxcoloring.IHighlightingConfigurationAcceptor;
import org.eclipse.xtext.ui.editor.utils.TextStyle;

/**
 * Defines syntax highlighting styles for SAPL. This configuration provides
 * consistent highlighting across Eclipse, VS Code, and Neovim.
 *
 * <p>Inherits keyword, string, number, comment, etc. from
 * {@link DefaultHighlightingConfiguration} and overrides their styles. Adds
 * SAPL-specific highlighting for entitlements, authorization variables,
 * functions, attributes, etc.
 */
public class SAPLHighlightingConfiguration extends DefaultHighlightingConfiguration {

    /** Entitlements (permit, deny) and combining algorithms. */
    public static final String ENTITLEMENT_ID = "sapl.entitlement";

    /** Authorization subscription variables: subject, action, resource, environment. */
    public static final String AUTHZ_VAR_ID = "sapl.authzVar";

    /** User-defined variables. */
    public static final String VARIABLE_ID = "sapl.variable";

    /** Function calls. */
    public static final String FUNCTION_ID = "sapl.function";

    /** Attribute finders. */
    public static final String ATTRIBUTE_ID = "sapl.attribute";

    /** Policy and policy set names. */
    public static final String POLICY_NAME_ID = "sapl.policyName";

    /** Operators and structural symbols. */
    public static final String OPERATOR_ID = "sapl.operator";

    // Color definitions
    private static final RGB ORANGE       = new RGB(0xCC, 0x78, 0x32);
    private static final RGB GREEN        = new RGB(0x62, 0x97, 0x55);
    private static final RGB STRING_GREEN = new RGB(0x6A, 0x87, 0x59);
    private static final RGB BLUE         = new RGB(0x68, 0x97, 0xBB);
    private static final RGB PURPLE       = new RGB(0x98, 0x76, 0xAA);
    private static final RGB YELLOW       = new RGB(0xFF, 0xC6, 0x6D);
    private static final RGB TEAL         = new RGB(0x29, 0x99, 0x99);
    private static final RGB GRAY         = new RGB(0x80, 0x80, 0x80);
    private static final RGB SUBTLE_GRAY  = new RGB(0x5C, 0x63, 0x70);

    @Override
    public void configure(IHighlightingConfigurationAcceptor acceptor) {
        // Register parent styles (keyword, string, number, comment, etc.)
        super.configure(acceptor);

        // Register SAPL-specific styles
        acceptor.acceptDefaultHighlighting(ENTITLEMENT_ID, "Entitlements & Algorithms", entitlementTextStyle());
        acceptor.acceptDefaultHighlighting(AUTHZ_VAR_ID, "Authorization Variables", authzVarTextStyle());
        acceptor.acceptDefaultHighlighting(VARIABLE_ID, "Variables", variableTextStyle());
        acceptor.acceptDefaultHighlighting(FUNCTION_ID, "Functions", functionTextStyle());
        acceptor.acceptDefaultHighlighting(ATTRIBUTE_ID, "Attributes", attributeTextStyle());
        acceptor.acceptDefaultHighlighting(POLICY_NAME_ID, "Policy Names", policyNameTextStyle());
        acceptor.acceptDefaultHighlighting(OPERATOR_ID, "Operators", operatorTextStyle());
    }

    @Override
    public TextStyle keywordTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(ORANGE);
        return textStyle;
    }

    @Override
    public TextStyle stringTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(STRING_GREEN);
        return textStyle;
    }

    @Override
    public TextStyle numberTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(BLUE);
        return textStyle;
    }

    @Override
    public TextStyle commentTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(GRAY);
        textStyle.setStyle(SWT.ITALIC);
        return textStyle;
    }

    /**
     * Style for entitlements (permit, deny) and combining algorithms. Green color
     * with bold styling.
     */
    public TextStyle entitlementTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(GREEN);
        textStyle.setStyle(SWT.BOLD);
        return textStyle;
    }

    /**
     * Style for authorization subscription variables. Blue color with italic
     * styling.
     */
    public TextStyle authzVarTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(BLUE);
        textStyle.setStyle(SWT.ITALIC);
        return textStyle;
    }

    /**
     * Style for user-defined variables. Purple color.
     */
    public TextStyle variableTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(PURPLE);
        return textStyle;
    }

    /**
     * Style for function calls. Yellow color.
     */
    public TextStyle functionTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(YELLOW);
        return textStyle;
    }

    /**
     * Style for attribute finders. Teal color.
     */
    public TextStyle attributeTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(TEAL);
        return textStyle;
    }

    /**
     * Style for policy and policy set names. Blue color.
     */
    public TextStyle policyNameTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(BLUE);
        return textStyle;
    }

    /**
     * Style for operators and structural symbols. Subtle gray color.
     */
    public TextStyle operatorTextStyle() {
        var textStyle = defaultTextStyle().copy();
        textStyle.setColor(SUBTLE_GRAY);
        return textStyle;
    }

}
