#!/usr/bin/env bash
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

# Restore CPU defaults after benchmarking. Requires sudo.

set -e

echo "Enabling turbo boost..."
echo 0 | sudo tee /sys/devices/system/cpu/intel_pstate/no_turbo

echo "Restoring frequency range..."
echo 5600000 | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq >/dev/null
echo 800000 | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq >/dev/null

echo ""
echo "Verification:"
echo "  No turbo: $(cat /sys/devices/system/cpu/intel_pstate/no_turbo)"
echo "  Max freq: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq) kHz"
echo "  Min freq: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq) kHz"
echo ""
echo "CPU restored to defaults."
