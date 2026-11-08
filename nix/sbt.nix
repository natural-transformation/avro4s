{ pkgs, jre }:
let
  inherit (pkgs) lib;
  lock = builtins.fromJSON (builtins.readFile ./sbt-bootstrap.json);
  scala = lock.scalaVersion;
  sbtVersion = lock.sbtVersion;
  expectedVersion = lib.strings.trim (lib.removePrefix "sbt.version=" (builtins.readFile ../project/build.properties));
  boot = assert lib.assertMsg (sbtVersion == expectedVersion)
    "sbt.version and nix/sbt-bootstrap.json disagree; refresh the bootstrap lock";
    assert lib.assertMsg (lock.artifacts != []) "Bootstrap classpath is empty";
    pkgs.runCommand "avro4s-sbt-${sbtVersion}-boot" {} (''
    mkdir -p "$out/scala-${scala}/lib" "$out/scala-${scala}/org.scala-sbt/sbt/${sbtVersion}"
  '' + lib.concatStringsSep "\n" (map (artifact:
    let
      name = builtins.baseNameOf artifact.url;
      jar = pkgs.fetchurl { inherit (artifact) url sha256; };
      scalaJar = lib.findFirst (module: name == "${module}-${scala}.jar") null
        [ "scala-library" "scala-reflect" "scala-compiler" ];
      destination = if scalaJar == null
        then "scala-${scala}/org.scala-sbt/sbt/${sbtVersion}/${name}"
        else "scala-${scala}/lib/${scalaJar}.jar";
    in ''ln -sf ${jar} "$out/${destination}"'' + "\n"
      + lib.optionalString (lib.hasPrefix "scala-xml_2.12-" name)
        ''
          ln -sf ${jar} "$out/scala-${scala}/lib/${name}"
        '') lock.artifacts));
  runner = pkgs.sbt.override { inherit jre; };
in pkgs.symlinkJoin {
  name = "avro4s-sbt-${sbtVersion}";
  paths = [ runner ];
  nativeBuildInputs = [ pkgs.makeWrapper ];
  passthru = {
    bootstrap = boot;
    launcher = "${runner}/share/sbt/bin/sbt-launch.jar";
  };
  postBuild = ''
    # sbtix also seeds its offline build from share/sbt/boot.
    rm -rf "$out/share/sbt"
    mkdir -p "$out/share/sbt"
    for item in ${runner}/share/sbt/*; do
      ln -s "$item" "$out/share/sbt/$(basename "$item")"
    done
    rm -f "$out/share/sbt/boot"
    ln -s ${boot} "$out/share/sbt/boot"
    rm "$out/bin/sbt"
    # The runner processes SBT_OPTS after CLI options. Remove only conflicting
    # boot flags there; keep our path as one CLI argument even with spaces.
    makeWrapper ${runner}/bin/sbt "$out/bin/sbt" \
      --run 'boot_root="''${SBT_BOOT:-''${XDG_CACHE_HOME:-$HOME/.cache}/avro4s/sbt-boot}"' \
      --run 'boot_dir="$(${pkgs.python3}/bin/python3 ${../scripts/prepare_sbt_boot.py} ${boot} "$boot_root")"' \
      --run 'read -r -a supplied_options <<< "''${SBT_OPTS:-}"' \
      --run 'managed_options=(); for option in "''${supplied_options[@]}"; do case "$option" in -Dsbt.boot.directory=*|-J-Dsbt.boot.directory=*) ;; *) managed_options+=("$option");; esac; done' \
      --run 'export SBT_OPTS="''${managed_options[*]}"' \
      --append-flags '-Dsbt.boot.directory="$boot_dir"'
  '';
}
