{
  description = "OPA benchmark environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f {
        pkgs = import nixpkgs { inherit system; };
      });
    in {
      devShells = forAllSystems ({ pkgs }: {
        default = pkgs.mkShell {
          packages = [ pkgs.open-policy-agent ];
          shellHook = ''
            echo "OPA benchmark shell"
            echo "  opa version: $(opa version | head -1)"
            echo "  Run: ./run-opa-bench.sh"
          '';
        };
      });
    };
}
