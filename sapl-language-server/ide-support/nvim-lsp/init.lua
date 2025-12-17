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
-- Disable vim syntax highlighting for SAPL and SAPLTest files.
-- The LSP provides semantic tokens for all highlighting.
vim.api.nvim_create_autocmd('FileType', {
  pattern = { 'sapl', 'sapltest' },
  callback = function()
    vim.bo.syntax = ''
  end,
})

-- lazy.nvim bootstrap (skip if you use a different plugin manager)
local lazypath = vim.fn.stdpath('data') .. '/lazy/lazy.nvim'
if not vim.loop.fs_stat(lazypath) then
  vim.fn.system({
    'git', 'clone', '--filter=blob:none',
    'https://github.com/folke/lazy.nvim.git',
    '--branch=stable', lazypath,
  })
end
vim.opt.rtp:prepend(lazypath)

vim.opt.number = true
vim.opt.tabstop = 4
vim.opt.shiftwidth = 4
vim.opt.expandtab = true

require('lazy').setup({

  -- TREESITTER: Required for markdown highlighting in LSP documentation popups.
  -- The SAPL LSP sends markdown-formatted documentation. Without treesitter's
  -- markdown/markdown_inline parsers, documentation renders as raw text.
  {
    'nvim-treesitter/nvim-treesitter',
    build = ':TSUpdate',
    config = function()
      require('nvim-treesitter.configs').setup({
        -- markdown + markdown_inline: renders LSP documentation
        -- lua, vim, regex: noice.nvim dependencies
        ensure_installed = { 'markdown', 'markdown_inline', 'lua', 'vim', 'regex' },
        highlight = { enable = true },
      })
    end,
  },

  -- NOICE.NVIM: Overrides nvim-cmp's documentation rendering to use treesitter.
  -- Without this, nvim-cmp shows raw markdown in completion documentation.
  -- This is optional but significantly improves documentation readability.
  -- Note: noice.nvim changes cmdline, messages, and popupmenu UI globally.
  {
    'folke/noice.nvim',
    dependencies = { 'MunifTanjim/nui.nvim' },
    config = function()
      require('noice').setup({
        lsp = {
          override = {
            -- These three overrides route LSP markdown through treesitter
            ['vim.lsp.util.convert_input_to_markdown_lines'] = true,
            ['vim.lsp.util.stylize_markdown'] = true,
            ['cmp.entry.get_documentation'] = true, -- nvim-cmp documentation
          },
        },
        presets = {
          lsp_doc_border = true,
        },
      })
    end,
  },

  -- LSP: Custom SAPL language server registration
  {
    'neovim/nvim-lspconfig',
    config = function()
      require('sapl_lspconfig') -- see lua/sapl_lspconfig.lua
    end,
  },

  -- COMPLETION: Standard nvim-cmp setup with LSP source
  {
    'hrsh7th/nvim-cmp',
    dependencies = { 'hrsh7th/cmp-nvim-lsp' },
    config = function()
      local cmp = require('cmp')
      cmp.setup({
        window = {
          completion = cmp.config.window.bordered(),
          documentation = cmp.config.window.bordered(),
        },
        mapping = cmp.mapping.preset.insert({
          ['<C-Space>'] = cmp.mapping.complete(),
          ['<CR>'] = cmp.mapping.confirm({ select = true }),
          ['<Tab>'] = cmp.mapping.select_next_item(),
          ['<S-Tab>'] = cmp.mapping.select_prev_item(),
          ['<C-u>'] = cmp.mapping.scroll_docs(-4),
          ['<C-d>'] = cmp.mapping.scroll_docs(4),
        }),
        sources = { { name = 'nvim_lsp' } },
      })
    end,
  },

})

-- LSP semantic token highlight groups.
local function setup_semantic_highlights()
  local hl = vim.api.nvim_set_hl

  -- Keywords: orange
  hl(0, '@lsp.type.keyword', { fg = '#CC7832' })

  -- Macro: permit/deny and combining algorithms (green)
  hl(0, '@lsp.type.macro', { fg = '#629755', bold = true })

  -- Operators: subtle grey
  hl(0, '@lsp.type.operator', { fg = '#5c6370' })

  -- Strings: green
  hl(0, '@lsp.type.string', { fg = '#6A8759' })

  -- Numbers: blue
  hl(0, '@lsp.type.number', { fg = '#6897BB' })

  -- Comments: gray, italic
  hl(0, '@lsp.type.comment', { fg = '#808080', italic = true })

  -- Namespaces: policy set names (blue)
  hl(0, '@lsp.type.namespace', { fg = '#6897BB' })

  -- Classes: policy names (blue, same as namespace)
  hl(0, '@lsp.type.class', { fg = '#6897BB' })

  -- Variables: purple
  hl(0, '@lsp.type.variable', { fg = '#9876AA' })

  -- Functions: yellow
  hl(0, '@lsp.type.function', { fg = '#FFC66D' })

  -- Properties: attribute finders (teal)
  hl(0, '@lsp.type.property', { fg = '#299999' })

  -- Parameters: subject, action, resource, environment (cyan, italic)
  hl(0, '@lsp.type.parameter', { fg = '#6897BB', italic = true })
end

setup_semantic_highlights()
