# SAPL Node Nix Flake
#
# Provides the sapl-node binary package, a NixOS module for declarative
# PDP server deployment, and an overlay for integrating with nixpkgs.
#
# Supported platforms:
#   - x86_64-linux  (fully static, musl-linked, no runtime dependencies)
#   - aarch64-linux (static-nolibc, links glibc dynamically, auto-patched)
#
# Quick start:
#
#   # Add to your flake inputs:
#   sapl-node.url = "github:heutelbeck/sapl-policy-engine";
#
#   # Import the NixOS module:
#   imports = [ inputs.sapl-node.nixosModules.default ];
#
#   # Enable the service:
#   services.sapl-node = {
#     enable = true;
#     port = 8080;
#     tls.enable = false;
#     policies.hello = ''
#       policy "hello"
#       permit action == "read"
#     '';
#   };
#
# See nix/module.nix for the full option reference.
{
  description = "SAPL Node - Streaming Attribute Policy Language PDP Server";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs supportedSystems
        (system: f nixpkgs.legacyPackages.${system});
    in
    {
      # Per-platform packages. Use `nix build` or reference as a dependency.
      packages = forAllSystems (pkgs: {
        sapl-node = pkgs.callPackage ./nix/package.nix { };
        default = self.packages.${pkgs.system}.sapl-node;
      });

      # NixOS module providing services.sapl-node with full declarative config.
      nixosModules.default = import ./nix/module.nix self;

      # Overlay that adds sapl-node to the pkgs set.
      # Usage: { nixpkgs.overlays = [ inputs.sapl-node.overlays.default ]; }
      overlays.default = final: prev: {
        sapl-node = self.packages.${final.system}.sapl-node;
      };
    };
}
