{ pkgs ? import <nixpkgs> { } }:

with pkgs;

let
	android-nixpkgs = callPackage (import (builtins.fetchGit {
		url = "https://github.com/tadfisher/android-nixpkgs.git";
	})) {
		channel = "stable";
	};

	jdk = jdk17;
	gradle = gradle_8.override { java = jdk; };
	android-sdk = android-nixpkgs.sdk (sdkPkgs: with sdkPkgs; [
		cmdline-tools-latest
		build-tools-35-0-0
		platform-tools
		platforms-android-35
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
