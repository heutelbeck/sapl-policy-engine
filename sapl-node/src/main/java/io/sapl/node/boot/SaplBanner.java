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
package io.sapl.node.boot;

import java.io.PrintStream;

import org.springframework.boot.Banner;
import org.springframework.boot.ResourceBanner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

/**
 * Startup banner that prints the truecolor SAPL logo only on an interactive
 * terminal (honoring {@code NO_COLOR} and {@code FORCE_COLOR}) and plain ASCII
 * otherwise, so piped or redirected server output keeps no raw ANSI escapes.
 */
public class SaplBanner implements Banner {

    private static final Banner COLORED = new ResourceBanner(new ClassPathResource("banner-color.txt"));
    private static final Banner PLAIN   = new ResourceBanner(new ClassPathResource("banner-plain.txt"));

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        (colorEnabled() ? COLORED : PLAIN).printBanner(environment, sourceClass, out);
    }

    private static boolean colorEnabled() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        if (System.getenv("FORCE_COLOR") != null) {
            return true;
        }
        return System.console() != null;
    }

}
