{ pkgs ? import <nixpkgs> { } }:

with pkgs;

let
	android-nixpkgs = callPackage (import (builtins.fetchGit {
		url = "https://github.com/tadfisher/android-nixpkgs.git";
	})) {
		channel = "stable";
	};

	jdk = jdk11;
	gradle = gradle_7.override { java = jdk; };
	android-sdk = android-nixpkgs.sdk (sdkPkgs: with sdkPkgs; [
		cmdline-tools-latest
		build-tools-33-0-0
		platform-tools
		platforms-android-33
		emulator
	]);
in

mkShell {
  	buildInputs = [
		jdk
		gradle
    	android-sdk
		apksigner
	];
}
