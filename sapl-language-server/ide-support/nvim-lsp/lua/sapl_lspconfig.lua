--[[
    SAPL Language Server - nvim-lspconfig registration

    Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
    SPDX-License-Identifier: Apache-2.0

    Registers SAPL as a custom LSP with nvim-lspconfig.
    The SAPL LSP is not in nvim-lspconfig's server list, so manual registration is required.
]]

local lspconfig = require('lspconfig')
local configs = require('lspconfig.configs')

-- Register custom server definition (only if not already registered)
if not configs.sapl then
  configs.sapl = {
    default_config = {
      -- REQUIRED: Update path to your sapl-language-server JAR
      cmd = { 'java', '-jar', '/path/to/sapl-language-server-4.0.0-SNAPSHOT.jar' },

      filetypes = { 'sapl' },

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
-- The SAPL LSP provides comprehensive semantic tokens for syntax highlighting.
-- No separate syntax file needed - the LSP handles keywords, operators,
-- strings, numbers, comments, and semantic elements (functions, variables, etc.).
lspconfig.sapl.setup({})
