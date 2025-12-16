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
package io.sapl.compiler;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.SourceLocation;
import lombok.Getter;
import org.antlr.v4.runtime.ParserRuleContext;

import java.io.Serial;

/**
 * Thrown when SAPL compilation fails due to invalid policy structure or
 * unsupported constructs. Indicates a bug in the policy document or compiler
 * implementation.
 */
@Getter
public class SaplCompilerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final SourceLocation location;

    public SaplCompilerException() {
        super();
        this.location = null;
    }

    public SaplCompilerException(String message) {
        super(message);
        this.location = null;
    }

    public SaplCompilerException(String message, Throwable cause) {
        super(message, cause);
        this.location = null;
    }

    public SaplCompilerException(Throwable cause) {
        super(cause);
        this.location = null;
    }

    public SaplCompilerException(String message, SourceLocation location) {
        super(message);
        this.location = location;
    }

    public SaplCompilerException(String message, Throwable cause, SourceLocation location) {
        super(message, cause);
        this.location = location;
    }

    public SaplCompilerException(String message, ParserRuleContext context) {
        super(message);
        this.location = SourceLocationUtil.fromContext(context);
    }

    public SaplCompilerException(String message, Throwable cause, ParserRuleContext context) {
        super(message, cause);
        this.location = SourceLocationUtil.fromContext(context);
    }
}
