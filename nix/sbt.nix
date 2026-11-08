{ pkgs, jre, bootstrapLock }:
let
  inherit (pkgs) lib;
  versioning = builtins.head bootstrapLock.versioning;
  scala = versioning.scalaVersion;
  sbtVersion = versioning.sbtVersion;
  jars = lib.filterAttrs (path: _: lib.hasSuffix ".jar" path
    && !(lib.hasSuffix "-sources.jar" path)
    && !(lib.hasSuffix "-javadoc.jar" path)) bootstrapLock.artifacts;
  boot = pkgs.runCommand "avro4s-sbt-${sbtVersion}-boot" {} (''
    mkdir -p "$out/scala-${scala}/lib" "$out/scala-${scala}/org.scala-sbt/sbt/${sbtVersion}"
  '' + lib.concatStringsSep "\n" (lib.mapAttrsToList (path: artifact:
    let
      // sbtix also emits Ivy aliases for the same Maven artifact.
      name = builtins.baseNameOf artifact.url;
      jar = pkgs.fetchurl { inherit (artifact) url sha256; };
      scalaJar = lib.findFirst (module: name == "${module}-${scala}.jar") null
        [ "scala-library" "scala-reflect" "scala-compiler" ];
      destination = if scalaJar == null
        then "scala-${scala}/org.scala-sbt/sbt/${sbtVersion}/${name}"
        else "scala-${scala}/lib/${scalaJar}.jar";
    in ''ln -sf ${jar} "$out/${destination}"''
      + lib.optionalString (lib.hasPrefix "scala-xml_2.12-" name)
        ''
          ln -sf ${jar} "$out/scala-${scala}/lib/${name}"
        '') jars));
  runner = pkgs.sbt.override { inherit jre; };
in pkgs.symlinkJoin {
  name = "avro4s-sbt-${sbtVersion}";
  paths = [ runner ];
  nativeBuildInputs = [ pkgs.makeWrapper ];
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
    makeWrapper ${runner}/bin/sbt "$out/bin/sbt" \
      --run 'boot_root="''${SBT_BOOT:-''${XDG_CACHE_HOME:-$HOME/.cache}/avro4s/sbt-boot}"' \
      --run 'boot_dir="$boot_root/${builtins.baseNameOf boot}"' \
      --run 'mkdir -p "$boot_dir"' \
      --run 'cp -RL ${boot}/. "$boot_dir/"' \
      --run 'chmod -R u+w "$boot_dir"' \
      --append-flags '-Dsbt.boot.directory="$boot_dir"'
  '';
}
