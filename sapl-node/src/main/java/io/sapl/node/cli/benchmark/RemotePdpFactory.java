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
package io.sapl.node.cli.benchmark;

import javax.net.ssl.SSLException;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.remote.ProtobufRemotePolicyDecisionPoint;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Creates a {@link PolicyDecisionPoint} for remote connections from benchmark
 * context parameters. Supports both HTTP/JSON and RSocket/protobuf transports.
 */
@UtilityClass
public class RemotePdpFactory {

    static final String ERROR_BASIC_AUTH_FORMAT = "Error: --basic-auth must be in format 'user:password'.";

    public static PolicyDecisionPoint create(BenchmarkContext ctx) throws SSLException {
        if (ctx.rsocket()) {
            return createRsocket(ctx);
        }
        return createHttp(ctx);
    }

    private static PolicyDecisionPoint createHttp(BenchmarkContext ctx) throws SSLException {
        val builder = RemotePolicyDecisionPoint.builder().http().baseUrl(ctx.remoteUrl());
        if (ctx.insecure()) {
            builder.withUnsecureSSL();
        }
        if (ctx.basicAuth() != null) {
            val separatorIndex = ctx.basicAuth().indexOf(':');
            if (separatorIndex < 0) {
                throw new IllegalArgumentException(ERROR_BASIC_AUTH_FORMAT);
            }
            builder.basicAuth(ctx.basicAuth().substring(0, separatorIndex),
                    ctx.basicAuth().substring(separatorIndex + 1));
        } else if (ctx.token() != null) {
            builder.apiKey(ctx.token());
        }
        return builder.build();
    }

    private static PolicyDecisionPoint createRsocket(BenchmarkContext ctx) {
        val builder = ProtobufRemotePolicyDecisionPoint.builder().host(ctx.rsocketHost()).port(ctx.rsocketPort());
        if (ctx.basicAuth() != null) {
            val separatorIndex = ctx.basicAuth().indexOf(':');
            if (separatorIndex < 0) {
                throw new IllegalArgumentException(ERROR_BASIC_AUTH_FORMAT);
            }
            builder.basicAuth(ctx.basicAuth().substring(0, separatorIndex),
                    ctx.basicAuth().substring(separatorIndex + 1));
        } else if (ctx.token() != null) {
            builder.apiKey(ctx.token());
        }
        return builder.build();
    }

}
