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
package io.sapl.api.pdp.plugins;

import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibraryProvider;

import org.pf4j.ExtensionPoint;

/**
 * PF4J extension point through which a plugin contributes function libraries to
 * the PDP.
 * <p>
 * A plugin implements this interface; to be loaded from a plugin JAR by the
 * SAPL plugin engine the implementation is
 * additionally annotated with {@code org.pf4j.Extension}. The engine discovers
 * all such extensions, collects their
 * {@link #functionLibraries()}, and registers them with the function broker.
 * Each returned instance must be a fully
 * constructed object whose class carries the {@link FunctionLibrary}
 * annotation.
 * <p>
 * This interface extends {@link FunctionLibraryProvider}, so the very same
 * implementation can be discovered either
 * through PF4J (when packaged as a plugin JAR) or through the host
 * application's dependency injection (when placed on
 * the classpath and registered as a {@code FunctionLibraryProvider} bean, for
 * example in tests).
 *
 * @since 4.1.0
 */
public interface SaplFunctionLibraryPlugin extends FunctionLibraryProvider, ExtensionPoint {

}
