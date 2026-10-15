--[[

    Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)

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
-- Requires Neovim 0.11+; no plugin needed.
vim.lsp.config('sapl', {
  -- REQUIRED: Pick one of the following and update the path.
  -- Native binary (no Java required):
  cmd = { vim.fn.expand('~/.local/bin/sapl-language-server') },
  -- Standalone JAR (requires Java 21+):
  -- cmd = { 'java', '-jar', '/path/to/sapl-language-server-4.2.0-SNAPSHOT-standalone.jar' },

  filetypes = { 'sapl', 'sapltest' },
  root_markers = { 'pdp.json', '.git' },
})

-- Activate the server.
-- The SAPL LSP provides semantic tokens for syntax highlighting
-- for both .sapl and .sapltest files. No separate syntax file needed - the LSP
-- handles keywords, operator, strings, numbers, comments, and semantic elements.
vim.lsp.enable('sapl')
