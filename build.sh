#!/usr/bin/env bash

nix-build --arg pkgs '
import <nixpkgs> {
  overlays = [
    (self: super: {
      jdk = super.jdk21;
      jre = super.jdk21;
      sbt = super.sbt.override {
        jre = super.jdk21;
      };
    })
  ];
}
'

