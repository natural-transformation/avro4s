{
  description = "Flake for dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    sbtix.url = "github:natural-transformation/sbtix";
  };

  outputs = { self, nixpkgs, flake-utils, sbtix, mach-nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        jdk21-overlay = self: super: {
          jdk = super.jdk21;
          jre = super.jdk21;
          sbt = super.sbt.override { jre = super.jdk21; };
        };
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ jdk21-overlay ];
        };
        libPath = nixpkgs.lib.makeLibraryPath [ pkgs.lmdb ];
        sbtixPkg = import sbtix { inherit pkgs; };
      in {
        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [
            sbt
            sbtixPkg
            jdk # currently openjdk 19
            coursier
          ];

          # environment variables go here
        };
      });
}
