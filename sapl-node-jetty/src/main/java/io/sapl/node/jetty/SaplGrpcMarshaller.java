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
package io.sapl.node.jetty;

import io.grpc.MethodDescriptor.Marshaller;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Thin gRPC {@link Marshaller} adapter that delegates to function-style
 * encoder / decoder pairs from {@code SaplProtobufCodec}. Lets us
 * expose the SAPL protobuf wire format on gRPC without generating Java
 * message classes from {@code .proto} files.
 */
@RequiredArgsConstructor
final class SaplGrpcMarshaller<T> implements Marshaller<T> {

    private final Writer<T> writer;
    private final Reader<T> reader;

    @Override
    @SneakyThrows
    public InputStream stream(T value) {
        return new ByteArrayInputStream(writer.write(value));
    }

    @Override
    @SneakyThrows
    public T parse(InputStream stream) {
        return reader.read(stream.readAllBytes());
    }

    @FunctionalInterface
    interface Writer<T> {
        byte[] write(T value) throws IOException;
    }

    @FunctionalInterface
    interface Reader<T> {
        T read(byte[] bytes) throws IOException;
    }
}
