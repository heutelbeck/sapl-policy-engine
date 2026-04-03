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

# Experiment: RSocket server benchmarks
# Scenarios x P-core counts x connection counts x virtual threads

SCENARIOS=(baseline rbac hospital-1 hospital-100 hospital-300)
CORE_SWEEP=(1 4 8)
CONN_SWEEP=(32 64)
RSOCKET_VT=256
TRANSPORT_SWEEP=(uds)
