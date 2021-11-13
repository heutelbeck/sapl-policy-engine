#
# Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

FROM adoptopenjdk/openjdk11:alpine
LABEL org.opencontainers.image.source=https://github.com/heutelbeck/sapl-policy-engine/sapl-server-lt
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
VOLUME /tmp
VOLUME /pdp/data
WORKDIR /pdp
ENV spring_profiles_active=docker
ARG JAR_FILE
ADD ${JAR_FILE} /pdp/pdp.jar
EXPOSE 8443
ENTRYPOINT ["java","-Dloader.path=file:/pdp/data/lib","-Djava.security.egd=file:/dev/./urandom", "-jar","/pdp/pdp.jar"]
