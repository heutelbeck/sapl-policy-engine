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

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import lombok.experimental.UtilityClass;

import java.util.function.Predicate;

@UtilityClass
public class Badger {

    public static final String ERROR   = "error";
    public static final String PRIMARY = "primary";
    public static final String SUCCESS = "success";

    private static final String THEME = "theme";
    private static final String BADGE = "badge ";

    public static <T> ComponentRenderer<Span, T> badgeRenderer(final Predicate<T> flagPredicate, String trueStyle,
            String falseStyle, String trueLabel, String falseLabel) {
        return new ComponentRenderer<>(source -> renderBadgeBasedOnFlag(flagPredicate.test(source), trueStyle,
                falseStyle, trueLabel, falseLabel));
    }

    private static Span renderBadgeBasedOnFlag(boolean flag, String trueStyle, String falseStyle, String trueLabel,
            String falseLabel) {
        return createBadge(flag ? trueLabel : falseLabel, flag ? trueStyle : falseStyle);
    }

    public static Span createBadge(String label, String style) {
        final var span = new Span();
        span.setText(label);
        final var theme = BADGE + style;
        span.getElement().setAttribute(THEME, theme);
        return span;
    }
}
