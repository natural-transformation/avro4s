{ pkgs, original, originalSbt, sbt }:
# Retain sbtix's prebuilt plugin, but run its CLI on this repository's patched
# sbt/JDK. A guarded replacement fails if the pinned upstream launcher changes.
pkgs.runCommand "avro4s-sbtix" {} ''
  mkdir -p "$out/bin"
  cp -L ${original}/bin/sbtix "$out/bin/sbtix"
  chmod u+w "$out/bin/sbtix"
  substituteInPlace "$out/bin/sbtix" \
    --replace-fail '${originalSbt}/bin/sbt' '${sbt}/bin/sbt'
  cat > "$out/bin/sbtix-gen" <<EOF
  #!${pkgs.runtimeShell}
  exec "$out/bin/sbtix" genNix
  EOF
  chmod +x "$out/bin/sbtix" "$out/bin/sbtix-gen"
''
