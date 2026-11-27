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
package io.sapl.node.rsocket.pdp;

import java.net.SocketAddress;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.DuplexConnection;
import io.rsocket.RSocketErrorException;
import io.rsocket.exceptions.RejectedSetupException;
import io.rsocket.exceptions.UnsupportedSetupException;
import io.rsocket.frame.FrameHeaderCodec;
import io.rsocket.frame.FrameType;
import io.rsocket.frame.SetupFrameCodec;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lets the node refuse a SETUP with a data MIME type it does not speak as
 * {@code UNSUPPORTED_SETUP}, so that a client in the SAPL binary format knows to
 * fall back to protobuf.
 * <p>
 * rsocket-java answers every refusal of a {@code SocketAcceptor} with
 * {@code REJECTED_SETUP}, whatever the type of the refusal. This interceptor
 * therefore reads the data MIME type of the SETUP frame as it arrives, and if
 * the node does not speak it, sends the refusal of that connection as
 * {@code UNSUPPORTED_SETUP}. Every other frame and refusal passes unchanged.
 */
final class UnsupportedSetupInterceptor implements DuplexConnectionInterceptor {

    /** The message of the refusal of a SETUP with a data MIME type the node does not speak. */
    static final String ERROR_UNSUPPORTED_DATA_MIME_TYPE = "Unsupported data MIME type";

    @Override
    public DuplexConnection apply(Type type, DuplexConnection connection) {
        return type == Type.SOURCE ? new RefusingConnection(connection) : connection;
    }

    /**
     * A connection that sends the refusal of a SETUP with an unsupported data MIME type as {@code UNSUPPORTED_SETUP}.
     */
    private static final class RefusingConnection implements DuplexConnection {

        private final DuplexConnection connection;
        private volatile boolean       unsupportedSetup;

        RefusingConnection(DuplexConnection connection) {
            this.connection = connection;
        }

        @Override
        public void sendErrorAndClose(RSocketErrorException error) {
            if (unsupportedSetup && error instanceof RejectedSetupException) {
                connection.sendErrorAndClose(new UnsupportedSetupException(ERROR_UNSUPPORTED_DATA_MIME_TYPE));
            } else {
                connection.sendErrorAndClose(error);
            }
        }

        @Override
        public void sendFrame(int streamId, ByteBuf frame) {
            connection.sendFrame(streamId, frame);
        }

        @Override
        public Flux<ByteBuf> receive() {
            return connection.receive().doOnNext(this::noteSetup);
        }

        private void noteSetup(ByteBuf frame) {
            if (FrameHeaderCodec.frameType(frame) == FrameType.SETUP
                    && WireFormat.ofMimeType(SetupFrameCodec.dataMimeType(frame)) == null) {
                unsupportedSetup = true;
            }
        }

        @Override
        public ByteBufAllocator alloc() {
            return connection.alloc();
        }

        @Override
        public SocketAddress remoteAddress() {
            return connection.remoteAddress();
        }

        @Override
        public Mono<Void> onClose() {
            return connection.onClose();
        }

        @Override
        public void dispose() {
            connection.dispose();
        }

        @Override
        public boolean isDisposed() {
            return connection.isDisposed();
        }

        @Override
        public double availability() {
            return connection.availability();
        }
    }
}
