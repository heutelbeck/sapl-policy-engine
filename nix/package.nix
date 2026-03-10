# SAPL Node binary package
#
# Fetches the pre-built sapl-node native binary from GitHub releases.
# The binary serves dual purpose: PDP server and CLI tool for bundle
# management and credential generation.
#
# Platform differences:
#
#   x86_64-linux   Fully static binary linked against musl libc.
#                  No runtime dependencies. Works on any Linux distro
#                  including NixOS without patching.
#
#   aarch64-linux  Static binary except for libc (--static-nolibc).
#                  Links dynamically against glibc because GraalVM does
#                  not yet ship musl static JDK libraries for aarch64.
#                  autoPatchelfHook patches the ELF interpreter and RPATH
#                  for NixOS compatibility.
#
# Updating hashes:
#
#   The CI publish job automatically computes SRI hashes after uploading
#   release archives and commits updated hashes to nix/hashes.json.
#   To compute hashes manually:
#
#     nix hash file --sri sapl-node-<version>-linux-amd64.tar.gz
#     nix hash file --sri sapl-node-<version>-linux-arm64.tar.gz

{ lib, stdenv, fetchurl, autoPatchelfHook, glibc }:

let
  version = "4.0.0-SNAPSHOT";

  # Maps Nix system identifiers to release archive platform suffixes.
  platformMap = {
    "x86_64-linux" = "linux-amd64";
    "aarch64-linux" = "linux-arm64";
  };

  # SRI hashes for each platform archive, loaded from hashes.json.
  # The CI publish job updates hashes.json automatically after each release.
  hashes = builtins.fromJSON (builtins.readFile ./hashes.json);

  platform = platformMap.${stdenv.hostPlatform.system}
    or (throw "sapl-node: unsupported platform ${stdenv.hostPlatform.system}");

  # The x86_64 binary is fully static (musl). No ELF patching needed.
  # The aarch64 binary links glibc dynamically and needs autoPatchelfHook.
  isFullyStatic = platform == "linux-amd64";

in
stdenv.mkDerivation {
  pname = "sapl-node";
  inherit version;

  src = fetchurl {
    url = "https://github.com/heutelbeck/sapl-policy-engine/releases/download/snapshot-node/sapl-node-${version}-${platform}.tar.gz";
    sha256 = hashes.${platform};
  };

  # The archive contains files at the top level (sapl-node, LICENSE, README.md),
  # not inside a subdirectory.
  sourceRoot = ".";

  nativeBuildInputs = lib.optionals (!isFullyStatic) [ autoPatchelfHook ];
  buildInputs = lib.optionals (!isFullyStatic) [ glibc ];

  installPhase = ''
    install -Dm755 sapl-node $out/bin/sapl-node
  '';

  # GraalVM native images should not be stripped.
  dontStrip = true;

  meta = {
    description = "SAPL Node PDP server and policy management CLI";
    homepage = "https://github.com/heutelbeck/sapl-policy-engine";
    license = lib.licenses.asl20;
    platforms = builtins.attrNames platformMap;
    mainProgram = "sapl-node";
  };
}
