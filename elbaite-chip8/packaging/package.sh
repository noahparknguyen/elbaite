#!/usr/bin/env bash
#
# Builds Achroite's native packages for the system this runs on, from the jar `mvn package`
# made: on Windows an installer (.exe) and a portable .zip, on Linux a .deb and a portable
# .tar.gz. Each carries a Java runtime of its own, so nothing else needs installing. jpackage
# cannot build for another system, so CI runs this once on each (packages.yml).
#
# From the repository root, after `mvn package`:
#
#     bash elbaite-chip8/packaging/package.sh
#
# The packages land in elbaite-chip8/target/packages, named after the jar, as in
# elbaite-chip8-1.0.0-windows-x64.exe. Installed, the app is called Achroite.
#
# The icons are the window's own, scaled up by whole pixels so the pixel art stays sharp:
# achroite.ico holds 16, 32, 48, 64, 128 and 256 pixels (the 16-pixel drawing at 1 and 3 times,
# the 32-pixel one at 1, 2, 4 and 8), and achroite.png is the 256.

set -euo pipefail

module=elbaite-chip8
packaging=$module/packaging
target=$module/target

jars=("$target/$module"-*.jar)
if [ ${#jars[@]} -ne 1 ] || [ ! -f "${jars[0]}" ]; then
    echo "Expected one $module jar in $target: run mvn package first." >&2
    exit 1
fi
jar=${jars[0]}
version=${jar##*/"$module"-}
version=${version%.jar}

case "$(uname -s)" in
    Linux) system=linux ;;
    MINGW* | MSYS* | CYGWIN*) system=windows ;;
    *) echo "No packages are built for $(uname -s)." >&2; exit 1 ;;
esac
arch=$(uname -m)
if [ "$arch" = x86_64 ]; then
    arch=x64
fi
name=$module-$version-$system-$arch

input=$target/package-input
work=$target/package-work
out=$target/packages
rm -rf "$input" "$work" "$out"
mkdir -p "$input" "$work" "$out"
cp "$jar" "$input"

# Said the same way by every package.
about=(
    --name Achroite
    --app-version "$version"
    --vendor "Noah Park-Nguyen"
    --copyright "Copyright (c) 2026 Noah Park-Nguyen"
    --description "A CHIP-8 emulator"
)

# The app image: the launcher, the jar, and a Java runtime cut down to the two modules the
# emulator uses. jdeps finds java.base and java.desktop, which has Swing, Java Sound and image
# loading in it.
if [ $system = windows ]; then
    icon=$packaging/achroite.ico
else
    icon=$packaging/achroite.png
fi
jpackage --type app-image "${about[@]}" \
    --icon "$icon" \
    --input "$input" \
    --main-jar "${jar##*/}" \
    --add-modules java.base,java.desktop \
    --dest "$work"

# The MIT licence goes with every copy, the portable ones included.
cp LICENSE "$work/Achroite/"

# The portable archive and the installer are both made from that one image.
if [ $system = windows ]; then
    (cd "$work" && jar --create --no-manifest --file "../packages/$name.zip" Achroite)

    # Installs for the user alone, so it needs no administrator, into a folder they may
    # change. The upgrade code stays the same in every version, so a newer installer
    # replaces an older install instead of adding a second one beside it.
    jpackage --type exe "${about[@]}" \
        --app-image "$work/Achroite" \
        --license-file LICENSE \
        --about-url https://github.com/noahparknguyen/elbaite \
        --win-per-user-install \
        --win-dir-chooser \
        --win-menu \
        --win-menu-group Achroite \
        --win-shortcut \
        --win-shortcut-prompt \
        --win-upgrade-uuid 45668a57-f1d1-4eda-874a-e06546614ec7 \
        --dest "$work"
    mv "$work/Achroite-$version.exe" "$out/$name.exe"
else
    tar -czf "$out/$name.tar.gz" -C "$work" Achroite

    # linux/ holds the package's install and removal scripts: jpackage's own, except that a
    # system with no desktop, and so no menu to add Achroite to, does not fail them.
    jpackage --type deb "${about[@]}" \
        --app-image "$work/Achroite" \
        --resource-dir "$packaging/linux" \
        --license-file LICENSE \
        --about-url https://github.com/noahparknguyen/elbaite \
        --linux-package-name achroite \
        --linux-deb-maintainer noahparknguyen@gmail.com \
        --linux-shortcut \
        --linux-menu-group Game \
        --linux-app-category games \
        --dest "$work"
    mv "$work"/achroite_"$version"_*.deb "$out/$name.deb"
fi

ls -l "$out"
