--[[

    Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)

    SPDX-License-Identifier: Apache-2.0

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

]]
local lspconfig = require('lspconfig')
local configs = require('lspconfig.configs')

-- Register custom server definition (only if not already registered)
if not configs.sapl then
  configs.sapl = {
    default_config = {
      -- REQUIRED: Update path to your sapl-language-server-antlr standalone JAR
      cmd = { 'java', '-jar', '/path/to/sapl-language-server-antlr-4.0.0-SNAPSHOT-standalone.jar' },

      filetypes = { 'sapl', 'sapltest' },

      -- root_dir: Returns project root for LSP workspace.
      -- Simple implementation uses file's directory.
      -- For multi-file projects, consider pattern matching for project markers.
      root_dir = function(fname)
        return vim.fn.fnamemodify(fname, ':h')
      end,

      settings = {},
    },
  }
end

-- Activate the server.
-- The SAPL LSP provides comprehensive semantic tokens for syntax highlighting
-- for both .sapl and .sapltest files. No separate syntax file needed - the LSP
-- handles keywords, operator, strings, numbers, comments, and semantic elements.
lspconfig.sapl.setup({})
