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

# Prepare CPU for benchmarking: disable turbo, fix frequency, set performance profile.
# Run manually before benchmark sessions. Requires sudo. Resets on reboot.

set -e

echo "Disabling turbo boost..."
echo 0 | sudo tee /sys/devices/system/cpu/intel_pstate/no_turbo

echo "Fixing CPU frequency to 4.0 GHz..."
echo 4000000 | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq >/dev/null
echo 4000000 | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq >/dev/null

echo "Setting performance power profile..."
powerprofilesctl set performance 2>/dev/null || true

echo ""
echo "Verification:"
echo "  No turbo: $(cat /sys/devices/system/cpu/intel_pstate/no_turbo)"
echo "  Frequency: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq) kHz"
echo "  Profile: $(powerprofilesctl get 2>/dev/null || echo "unknown")"
echo "  Governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)"
echo ""
echo "CPU is locked for benchmarking. Run reset-cpu.sh to restore defaults."
