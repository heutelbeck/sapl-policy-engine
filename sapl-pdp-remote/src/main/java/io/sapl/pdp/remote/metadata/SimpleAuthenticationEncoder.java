/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.pdp.remote.metadata;

import java.util.Map;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.metadata.AuthMetadataCodec;
import reactor.core.publisher.Flux;

public class SimpleAuthenticationEncoder extends AbstractEncoder<UsernamePasswordMetadata> {

    private static final MimeType AUTHENTICATION_MIME_TYPE = MimeTypeUtils
            .parseMimeType("message/x.rsocket.authentication.v0");

    private NettyDataBufferFactory defaultBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);

    public SimpleAuthenticationEncoder() {
        super(AUTHENTICATION_MIME_TYPE);
    }

    @Override
    public Flux<DataBuffer> encode(Publisher<? extends UsernamePasswordMetadata> inputStream,
            DataBufferFactory bufferFactory, ResolvableType elementType, MimeType mimeType, Map<String, Object> hints) {
        return Flux.from(inputStream)
                .map(credentials -> encodeValue(credentials, bufferFactory, elementType, mimeType, hints));
    }

    @Override
    public DataBuffer encodeValue(UsernamePasswordMetadata credentials, DataBufferFactory bufferFactory,
            ResolvableType valueType, MimeType mimeType, Map<String, Object> hints) {
        String                 username             = credentials.username();
        String                 password             = credentials.password();
        NettyDataBufferFactory factory              = nettyFactory(bufferFactory);
        ByteBufAllocator       allocator            = factory.getByteBufAllocator();
        ByteBuf                simpleAuthentication = AuthMetadataCodec.encodeSimpleMetadata(allocator,
                username.toCharArray(), password.toCharArray());
        return factory.wrap(simpleAuthentication);
    }

    private NettyDataBufferFactory nettyFactory(DataBufferFactory bufferFactory) {
        if (bufferFactory instanceof NettyDataBufferFactory nettyDataBufferFactory) {
            return nettyDataBufferFactory;
        }
        return this.defaultBufferFactory;
    }

}
