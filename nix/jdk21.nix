{ pkgs }:
let
  # nixpkgs still pins the August build. Use the October 2026 security update,
  # with hashes published on the official Temurin 21.0.13+9 GitHub release.
  sources = builtins.fromJSON (builtins.readFile ./jdk21-sources.json);
  platform = pkgs.stdenv.hostPlatform;
  os = if platform.isDarwin then "darwin" else "linux";
  base = "${pkgs.path}/pkgs/development/compilers/temurin-bin/jdk-${os}-base.nix";
in pkgs.callPackage (import base {
  sourcePerArch = {
    packageType = "jdk";
    vmType = "hotspot";
    ${platform.parsed.cpu.name} = sources.${platform.system};
  };
}) {}
