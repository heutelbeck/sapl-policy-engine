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

# Experiment: latency at controlled load via --rate
# RSocket transport, rate sweep per scenario, JVM + native (if available)

SCENARIOS_QUICK=(hospital-300 github-10)
SCENARIOS_FULL=(hospital-300 hospital-100 hospital-50 github-10 github-100 gdrive-10 gdrive-50 tinytodo-10 tinytodo-50)

# Rate steps as percentage of measured saturation throughput
LOAD_PCTS_QUICK=(1 10 50 90)
LOAD_PCTS_FULL=(1 5 10 25 50 75 90)

RSOCKET_CONNECTIONS=8
RSOCKET_CONCURRENCY=512
SERVER_PCORES_SWEEP=(1 4 8)
