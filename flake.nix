{
  description = "Flake for dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    sbtix.url = "github:natural-transformation/sbtix/v1.2.8";
    gitignore = {
      url = "github:hercules-ci/gitignore.nix";
      # Use the same nixpkgs
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = inputs@{ nixpkgs, flake-parts, sbtix, gitignore, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" "aarch64-linux" "aarch64-darwin" ];
      perSystem = { config, self', inputs', pkgs, system, ... }: 
      let
        javaPkgs = version: import nixpkgs {
          inherit system;
          overlays = [ (self: super: let
            jdk = if version == "21" then import ./nix/jdk21.nix { pkgs = super; }
              else super."jdk${version}";
          in {
            inherit jdk;
            jre = jdk;
            sbt = import ./nix/sbt.nix {
              pkgs = super;
              jre = jdk;
              bootstrapLock = import ./sbt-bootstrap-repo.nix;
            };
          }) ];
        };
        newPkgs = javaPkgs "21";
        # Import the sbtix.nix file directly, not the flake's default.nix (which is a stub)
        sbtixPkg = newPkgs.callPackage "${sbtix}/plugin/nix-exprs/sbtix.nix" {
          # sbtix 1.2.8 uses the old fold alias, removed by current nixpkgs.
          lib = newPkgs.lib // { fold = newPkgs.lib.foldr; };
        };
        # Get the sbtix CLI tool from the flake for devShell
        sbtixCli = inputs'.sbtix.packages.sbtix;
        javaShell = version: let tools = javaPkgs version; in tools.mkShell {
          nativeBuildInputs = [ tools.sbt sbtixCli tools.jdk tools.python3 tools.osv-scanner ];
          JAVA_HOME = "${tools.jdk}";
          NIX_PATH = "nixpkgs=${inputs.nixpkgs}";
        };
      in {
        # Make packages.default lazy - wrap in a function that's only called when needed
        # This prevents it from being evaluated when only devShells is needed
        packages.default = (import ./default.nix { 
          pkgs = newPkgs; 
          gitignore = gitignore.lib;
          sbtix = sbtixPkg;
        });

        devShells = {
          default = javaShell "21";
          java21 = javaShell "21";
          java25 = javaShell "25";
        };
      };
     
      flake = {
        # The usual flake attributes can be defined here, including system-
        # agnostic ones like nixosModule and system-enumerating ones, although
        # those are more easily expressed in perSystem.
      };
    };
}
