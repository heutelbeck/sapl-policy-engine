#!/bin/sh
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

set -e
if ! getent group sapl-node >/dev/null 2>&1; then
    groupadd --system sapl-node
fi
if ! getent passwd sapl-node >/dev/null 2>&1; then
    useradd --system --gid sapl-node --home-dir /var/lib/sapl-node --shell /usr/sbin/nologin --no-create-home sapl-node
fi
chown -R sapl-node:sapl-node /var/lib/sapl-node
systemctl daemon-reload
