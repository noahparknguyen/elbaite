#!/usr/bin/env bash
#
# Builds Achroite's native packages for the system this runs on, from the jar the build made:
# on Windows an installer (.exe) and a portable .zip, on Linux a .deb and a portable .tar.gz.
# Each carries a Java runtime of its own, so nothing else needs installing. jpackage cannot
# build for another system, so CI runs this once on each (packages.yml).
#
# From the repository root, after `./mvnw package`:
#
#     bash elbaite-chip8/packaging/package.sh
#
# The packages land in elbaite-chip8/target/packages, named after the jar, as in
# elbaite-chip8-1.0.0-windows-x64.exe. Installed, the app is called Achroite.
#
# Each system's package resources are in its own folder, linux/ or windows/: the icon, named
# after the app as jpackage expects, and on Linux the install and removal scripts. The icons
# are the window's own, scaled up by whole pixels so the pixel art stays sharp: Achroite.ico
# holds 16, 32, 48, 64, 128 and 256 pixels (the 16-pixel drawing at 1 and 3 times, the 32-pixel
# one at 1, 2, 4 and 8), and Achroite.png is the 256.

set -euo pipefail

module=elbaite-chip8
packaging=$module/packaging
target=$module/target

jars=("$target/$module"-*.jar)
if [ ${#jars[@]} -ne 1 ] || [ ! -f "${jars[0]}" ]; then
    echo "Expected one $module jar in $target: run ./mvnw package first." >&2
    exit 1
fi
jar=${jars[0]}
version=${jar##*/"$module"-}
version=${version%.jar}

case "$(uname -s)" in
    Linux) system=linux icon=Achroite.png ;;
    MINGW* | MSYS* | CYGWIN*) system=windows icon=Achroite.ico ;;
    *) echo "No packages are built for $(uname -s)." >&2; exit 1 ;;
esac
resources=$packaging/$system
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
# loading in it. -XX:-UsePerfData stops Java leaving an hsperfdata folder in the system's
# temporary folder, which only monitoring tools read, so the app writes nothing at all.
jpackage --type app-image "${about[@]}" \
    --icon "$resources/$icon" \
    --input "$input" \
    --main-jar "${jar##*/}" \
    --add-modules java.base,java.desktop \
    --java-options -XX:-UsePerfData \
    --dest "$work"

# The MIT licence goes with every copy, the portable ones included.
cp LICENSE "$work/Achroite/"

# The portable archive and the installer are both made from that one image.
if [ $system = windows ]; then
    (cd "$work" && jar --create --no-manifest --file "../packages/$name.zip" Achroite)

    # Installs for the user alone, so it needs no administrator, always into
    # %LOCALAPPDATA%\Achroite. There is deliberately no folder chooser: jpackage's uninstaller
    # deletes the install folder and everything in it, and a chosen folder that already held
    # the user's files (it only warns, and lets them go ahead) would lose them all. The upgrade
    # code stays the same in every version, so a newer installer replaces an older install
    # instead of adding a second one beside it. The installer file's own icon comes from
    # --icon alone (jpackage's WinExeBundler), not the resource folder.
    jpackage --type exe "${about[@]}" \
        --app-image "$work/Achroite" \
        --icon "$resources/$icon" \
        --resource-dir "$resources" \
        --license-file LICENSE \
        --about-url https://github.com/noahparknguyen/elbaite \
        --win-per-user-install \
        --win-menu \
        --win-menu-group Achroite \
        --win-shortcut \
        --win-shortcut-prompt \
        --win-upgrade-uuid 45668a57-f1d1-4eda-874a-e06546614ec7 \
        --dest "$work"
    mv "$work/Achroite-$version.exe" "$out/$name.exe"
else
    tar -czf "$out/$name.tar.gz" -C "$work" Achroite

    # The resource folder gives the package its icon, since a .deb built from an app image
    # ignores --icon, and its install and removal scripts: jpackage's own, except that a system
    # with no desktop, and so no menu to add Achroite to, does not fail them. Its control file
    # is jpackage's too, with the dependencies written out: jpackage's own list, but the sound
    # library as libasound2, the name Ubuntu 22.04 and Debian 12 know, where it found
    # libasound2t64, which newer systems also install for libasound2; and fontconfig, which
    # brings a font. Java loads fontconfig only when the window opens, so jpackage cannot see
    # it, and on a system with no fonts at all the window fails to open.
    jpackage --type deb "${about[@]}" \
        --app-image "$work/Achroite" \
        --resource-dir "$resources" \
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
