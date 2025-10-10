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
package io.sapl.playground.ui.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.function.Predicate;

/**
 * Utility class for creating badge components in Vaadin grids.
 * Provides factory methods for creating styled badge spans and
 * component renderers that conditionally display badges based
 * on data properties.
 * <p>
 * Badges are used to visually highlight status or boolean properties
 * in data grids, using Vaadin's theme variants (error, primary, success).
 */
@UtilityClass
public class Badger {

    /*
     * Theme variant for error badges.
     * Typically displayed in red to indicate negative status.
     */
    public static final String ERROR = "error";

    /*
     * Theme variant for primary badges.
     * Typically displayed in blue to indicate important or active status.
     */
    public static final String PRIMARY = "primary";

    /*
     * Theme variant for success badges.
     * Typically displayed in green to indicate positive status.
     */
    public static final String SUCCESS = "success";

    /*
     * Attribute name for setting Vaadin component theme.
     */
    private static final String THEME = "theme";

    /*
     * Badge theme prefix that precedes the style variant.
     */
    private static final String BADGE = "badge ";

    /**
     * Creates a component renderer that displays conditional badges.
     * Evaluates a predicate against grid items and displays different
     * badges based on whether the condition is met.
     * <p>
     * Commonly used in Vaadin grids to show boolean properties as
     * styled badges instead of true/false text.
     *
     * @param <T> the type of items in the grid
     * @param conditionPredicate predicate to evaluate against each item
     * @param styleWhenTrue theme style when predicate returns true
     * @param styleWhenFalse theme style when predicate returns false
     * @param labelWhenTrue text to display when predicate returns true
     * @param labelWhenFalse text to display when predicate returns false
     * @return component renderer that creates badges based on the condition
     */
    public static <T> ComponentRenderer<Span, T> badgeRenderer(final Predicate<T> conditionPredicate,
            String styleWhenTrue, String styleWhenFalse, String labelWhenTrue, String labelWhenFalse) {
        return new ComponentRenderer<>(item -> {
            boolean conditionMet = conditionPredicate.test(item);
            String  label        = conditionMet ? labelWhenTrue : labelWhenFalse;
            String  style        = conditionMet ? styleWhenTrue : styleWhenFalse;
            return createBadge(label, style);
        });
    }

    /**
     * Creates a badge span with the specified label and style.
     * Applies Vaadin badge theme with the provided style variant.
     *
     * @param label text to display in the badge
     * @param style theme style variant (ERROR, PRIMARY, or SUCCESS)
     * @return styled badge span component
     */
    public static Span createBadge(String label, String style) {
        val badgeSpan = new Span();
        badgeSpan.setText(label);
        val themeValue = BADGE + style;
        badgeSpan.getElement().setAttribute(THEME, themeValue);
        return badgeSpan;
    }
}
