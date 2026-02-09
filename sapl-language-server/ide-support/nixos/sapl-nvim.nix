# NixOS / Home Manager: Neovim with SAPL Language Server
#
# This module configures Neovim with full SAPL LSP support including
# completion, diagnostics, and semantic token highlighting.
#
# The SAPL language server binary is fetched from the latest GitHub
# snapshot release:
#   https://github.com/heutelbeck/sapl-policy-engine/releases/tag/snapshot
#
# Usage with Home Manager (standalone or as NixOS module):
#
#   sapl-language-server-bin = builtins.fetchurl {
#     url = "https://github.com/heutelbeck/sapl-policy-engine/releases/download/snapshot/sapl-language-server-linux-amd64";
#     sha256 = "0000000000000000000000000000000000000000000000000000";  # nix will tell you the correct hash
#   };
#
#   # Then import this module:
#   imports = [ ./sapl-nvim.nix ];
#
# Or inline the relevant parts into your existing Neovim configuration.

{ pkgs, sapl-language-server-bin, ... }:
let
  sapl-language-server = pkgs.stdenv.mkDerivation {
    pname = "sapl-language-server";
    version = "snapshot";
    dontUnpack = true;
    installPhase = ''
      install -Dm755 ${sapl-language-server-bin} $out/bin/sapl-language-server
    '';
  };
in
{
  programs.neovim = {
    enable = true;

    # Make the SAPL language server binary available to neovim
    extraPackages = [ sapl-language-server ];

    plugins = with pkgs.vimPlugins; [
      # LSP client
      nvim-lspconfig

      # Completion engine and its LSP source
      nvim-cmp
      cmp-nvim-lsp

      # Treesitter for markdown rendering in LSP hover/documentation popups
      (nvim-treesitter.withPlugins (p: [
        p.markdown
        p.markdown_inline
      ]))
    ];

    initLua = ''
      -- Register .sapl and .sapltest filetypes
      vim.filetype.add({
        extension = {
          sapl = "sapl",
          sapltest = "sapltest",
        },
      })

      -- Disable vim regex syntax highlighting for SAPL files;
      -- the LSP provides semantic token highlighting instead
      vim.api.nvim_create_autocmd("FileType", {
        pattern = { "sapl", "sapltest" },
        callback = function()
          vim.bo.syntax = ""
        end,
      })

      -- Register the SAPL language server with nvim-lspconfig
      local lspconfig = require("lspconfig")
      local configs = require("lspconfig.configs")

      if not configs.sapl then
        configs.sapl = {
          default_config = {
            cmd = { "sapl-language-server" },
            filetypes = { "sapl", "sapltest" },
            root_dir = function(fname)
              return vim.fn.fnamemodify(fname, ":h")
            end,
            settings = {},
          },
        }
      end

      lspconfig.sapl.setup({
        capabilities = require("cmp_nvim_lsp").default_capabilities(),
      })

      -- Completion setup
      local cmp = require("cmp")
      cmp.setup({
        window = {
          completion = cmp.config.window.bordered(),
          documentation = cmp.config.window.bordered(),
        },
        mapping = cmp.mapping.preset.insert({
          ["<C-Space>"] = cmp.mapping.complete(),
          ["<CR>"] = cmp.mapping.confirm({ select = true }),
          ["<Tab>"] = cmp.mapping.select_next_item(),
          ["<S-Tab>"] = cmp.mapping.select_prev_item(),
          ["<C-u>"] = cmp.mapping.scroll_docs(-4),
          ["<C-d>"] = cmp.mapping.scroll_docs(4),
        }),
        sources = { { name = "nvim_lsp" } },
      })

      -- Semantic token highlight colors for SAPL
      local hl = vim.api.nvim_set_hl
      hl(0, "@lsp.type.keyword",   { fg = "#CC7832" })                -- orange
      hl(0, "@lsp.type.macro",     { fg = "#629755", bold = true })   -- green, bold
      hl(0, "@lsp.type.operator",  { fg = "#5c6370" })                -- subtle grey
      hl(0, "@lsp.type.string",    { fg = "#6A8759" })                -- green
      hl(0, "@lsp.type.number",    { fg = "#6897BB" })                -- blue
      hl(0, "@lsp.type.comment",   { fg = "#808080", italic = true }) -- grey, italic
      hl(0, "@lsp.type.namespace", { fg = "#6897BB" })                -- blue
      hl(0, "@lsp.type.class",     { fg = "#6897BB" })                -- blue
      hl(0, "@lsp.type.variable",  { fg = "#9876AA" })                -- purple
      hl(0, "@lsp.type.function",  { fg = "#FFC66D" })                -- yellow
      hl(0, "@lsp.type.property",  { fg = "#299999" })                -- teal
      hl(0, "@lsp.type.parameter", { fg = "#6897BB", italic = true }) -- blue, italic
    '';
  };
}
