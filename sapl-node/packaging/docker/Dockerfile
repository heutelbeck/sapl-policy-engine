#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Minimal SAPL Node image: static musl native binary on distroless/static.
#
# No JVM, no shell, no package manager, no libc the binary uses. This minimizes
# image size and attack surface and gives millisecond startup, at the cost of
# peak throughput (no JIT), the serial collector, and no runtime-loadable
# extensions.
#
# Runtime contract mirrors the JVM image (config path, Spring profile, ports) so
# this image is a drop-in replacement, with one exception: a native binary
# cannot load extension jars at runtime, so there is no LOADER_PATH equivalent.
# Deployments that need third-party PIP or function-library jars use the JVM
# image.
#
# Build context must contain the static musl binary. Default name is `sapl`;
# override SAPL_BINARY for per-architecture build contexts in CI.

FROM gcr.io/distroless/static-debian12:nonroot

ARG SAPL_BINARY=sapl
COPY ${SAPL_BINARY} /sapl

ENV SPRING_PROFILES_ACTIVE=docker \
    SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/pdp/data/

EXPOSE 8080 7000

ENTRYPOINT ["/sapl"]
CMD ["server"]
