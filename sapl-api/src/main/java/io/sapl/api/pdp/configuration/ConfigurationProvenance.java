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
package io.sapl.api.pdp.configuration;

import java.io.Serializable;

/**
 * Records where a {@link PDPConfiguration} originated: a filesystem directory, a
 * bundle, a remote source, or an unknown origin. Provenance is descriptive
 * metadata only; it never influences policy decisions and never enters the
 * content-derived configuration id.
 * <p>
 * This interface is deliberately <em>not</em> sealed so that variants can live
 * in the module where their source machinery lives. Bundle provenance, for
 * example, belongs beside the bundle machinery in the PDP module, which the API
 * module must not depend on. Implementations must be {@link Serializable},
 * because {@link PDPConfiguration} is serializable and carries a provenance.
 */
public interface ConfigurationProvenance extends Serializable {
}
