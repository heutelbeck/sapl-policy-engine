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

import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.Executors;

/**
 * Reactor-free gRPC face on the {@link BlockingPolicyDecisionPoint},
 * built directly from manual {@link MethodDescriptor} definitions over
 * {@code SaplProtobufCodec}. Implements the four methods of
 * {@code io.sapl.api.proto.PolicyDecisionPointService}:
 * <ul>
 * <li>{@code DecideOnce} — unary single decision.</li>
 * <li>{@code Decide} — server-streaming subscription.</li>
 * <li>{@code DecideAll} — server-streaming bundled multi-subscription.</li>
 * <li>{@code MultiDecide} — server-streaming per-sub multi-subscription.</li>
 * </ul>
 * Streaming handlers spawn one virtual thread per call that pulls from
 * the SAPL {@link io.sapl.api.stream.Stream} and pushes via the gRPC
 * {@link StreamObserver}. No Reactor in the request path; the server
 * worker executor is also a virtual-thread executor.
 */
@Slf4j
final class SaplGrpcServer {

    private static final String SERVICE_NAME = "io.sapl.api.proto.PolicyDecisionPointService";

    private final BlockingPolicyDecisionPoint pdp;

    SaplGrpcServer(BlockingPolicyDecisionPoint pdp) {
        this.pdp = pdp;
    }

    Server start(int port) throws Exception {
        val service = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(unaryMethod("DecideOnce", SaplProtobufCodec::writeAuthorizationSubscription,
                        SaplProtobufCodec::readAuthorizationSubscription, SaplProtobufCodec::writeAuthorizationDecision,
                        SaplProtobufCodec::readAuthorizationDecision),
                        ServerCalls.asyncUnaryCall(this::handleDecideOnce))
                .addMethod(serverStreamMethod("Decide", SaplProtobufCodec::writeAuthorizationSubscription,
                        SaplProtobufCodec::readAuthorizationSubscription, SaplProtobufCodec::writeAuthorizationDecision,
                        SaplProtobufCodec::readAuthorizationDecision),
                        ServerCalls.asyncServerStreamingCall(this::handleDecide))
                .addMethod(
                        serverStreamMethod("DecideAll", SaplProtobufCodec::writeMultiAuthorizationSubscription,
                                SaplProtobufCodec::readMultiAuthorizationSubscription,
                                SaplProtobufCodec::writeMultiAuthorizationDecision,
                                SaplProtobufCodec::readMultiAuthorizationDecision),
                        ServerCalls.asyncServerStreamingCall(this::handleDecideAll))
                .addMethod(
                        serverStreamMethod("MultiDecide", SaplProtobufCodec::writeMultiAuthorizationSubscription,
                                SaplProtobufCodec::readMultiAuthorizationSubscription,
                                SaplProtobufCodec::writeIdentifiableAuthorizationDecision,
                                SaplProtobufCodec::readIdentifiableAuthorizationDecision),
                        ServerCalls.asyncServerStreamingCall(this::handleMultiDecide))
                .build();

        return NettyServerBuilder.forPort(port).executor(Executors.newVirtualThreadPerTaskExecutor())
                .addService(service).build().start();
    }

    private void handleDecideOnce(AuthorizationSubscription sub, StreamObserver<AuthorizationDecision> obs) {
        try {
            obs.onNext(pdp.decideOnce(sub));
            obs.onCompleted();
        } catch (Throwable failure) {
            obs.onError(failure);
        }
    }

    private void handleDecide(AuthorizationSubscription sub, StreamObserver<AuthorizationDecision> obs) {
        Thread.startVirtualThread(() -> pumpDecide(sub, obs));
    }

    private void handleDecideAll(MultiAuthorizationSubscription multi, StreamObserver<MultiAuthorizationDecision> obs) {
        Thread.startVirtualThread(() -> pumpDecideAll(multi, obs));
    }

    private void handleMultiDecide(MultiAuthorizationSubscription multi,
            StreamObserver<IdentifiableAuthorizationDecision> obs) {
        Thread.startVirtualThread(() -> pumpMultiDecide(multi, obs));
    }

    private void pumpDecide(AuthorizationSubscription sub, StreamObserver<AuthorizationDecision> obs) {
        try (val stream = pdp.decide(sub)) {
            while (!Thread.currentThread().isInterrupted()) {
                val next = stream.awaitNext();
                if (next == null) {
                    obs.onCompleted();
                    return;
                }
                obs.onNext(next);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            obs.onError(failure);
        }
    }

    private void pumpDecideAll(MultiAuthorizationSubscription multi, StreamObserver<MultiAuthorizationDecision> obs) {
        try (val stream = pdp.decideAll(multi)) {
            while (!Thread.currentThread().isInterrupted()) {
                val next = stream.awaitNext();
                if (next == null) {
                    obs.onCompleted();
                    return;
                }
                obs.onNext(next);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            obs.onError(failure);
        }
    }

    private void pumpMultiDecide(MultiAuthorizationSubscription multi,
            StreamObserver<IdentifiableAuthorizationDecision> obs) {
        try (val stream = pdp.decide(multi)) {
            while (!Thread.currentThread().isInterrupted()) {
                val next = stream.awaitNext();
                if (next == null) {
                    obs.onCompleted();
                    return;
                }
                obs.onNext(next);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            obs.onError(failure);
        }
    }

    private static <Req, Res> MethodDescriptor<Req, Res> unaryMethod(String name,
            SaplGrpcMarshaller.Writer<Req> reqWrite, SaplGrpcMarshaller.Reader<Req> reqRead,
            SaplGrpcMarshaller.Writer<Res> resWrite, SaplGrpcMarshaller.Reader<Res> resRead) {
        return MethodDescriptor.<Req, Res>newBuilder().setType(MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, name))
                .setRequestMarshaller(new SaplGrpcMarshaller<>(reqWrite, reqRead))
                .setResponseMarshaller(new SaplGrpcMarshaller<>(resWrite, resRead)).build();
    }

    private static <Req, Res> MethodDescriptor<Req, Res> serverStreamMethod(String name,
            SaplGrpcMarshaller.Writer<Req> reqWrite, SaplGrpcMarshaller.Reader<Req> reqRead,
            SaplGrpcMarshaller.Writer<Res> resWrite, SaplGrpcMarshaller.Reader<Res> resRead) {
        return MethodDescriptor.<Req, Res>newBuilder().setType(MethodType.SERVER_STREAMING)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, name))
                .setRequestMarshaller(new SaplGrpcMarshaller<>(reqWrite, reqRead))
                .setResponseMarshaller(new SaplGrpcMarshaller<>(resWrite, resRead)).build();
    }
}
