{
  description = "Flake for dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    sbtix.url = "github:natural-transformation/sbtix";
  };

  outputs = inputs@{ nixpkgs, flake-parts, sbtix, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" ];
      perSystem = { config, self', inputs', pkgs, system, ... }: 
      let
        jdk21-overlay = self: super: {
          jdk = super.jdk21;
          jre = super.jdk21;
          sbt = super.sbt.override { jre = super.jdk21; };
        };
        newPkgs = import nixpkgs {
          inherit system;
          overlays = [ jdk21-overlay ];
        };
        libPath = nixpkgs.lib.makeLibraryPath [ newPkgs.lmdb ];
        sbtixPkg = import sbtix { pkgs = newPkgs; };
      in {
        packages.default = import ./default.nix { pkgs = newPkgs; };

        devShells.default = newPkgs.mkShell {
          nativeBuildInputs = with newPkgs; [
            sbt
            sbtixPkg
            jdk
          ];
          # environment variables go here:
          # Set NIX_PATH to make sure the nix-build in `build.sh` produce the same results as `nix build`
          NIX_PATH = "nixpkgs=${inputs.nixpkgs}"; 
        };
      };
     
      flake = {
        # The usual flake attributes can be defined here, including system-
        # agnostic ones like nixosModule and system-enumerating ones, although
        # those are more easily expressed in perSystem.
      };
    };
}
