#!/bin/sh
set -eu

usage() {
    cat <<USAGE
Usage: ./install.sh [--clean] [--dest DIR]

Copies every .kts script from this repository into the folder the Darkan client
scans for scripts, overwriting files of the same name. Press "Reload scripts"
in the bot sidebar afterwards.

  --clean     Also delete .kts files in the destination that are not in this repository
  --dest DIR  Install somewhere other than the detected folder (also: DARKAN_SCRIPTS_DIR)
USAGE
}

clean=0
dest="${DARKAN_SCRIPTS_DIR:-}"
while [ $# -gt 0 ]; do
    case "$1" in
        --clean) clean=1 ;;
        --dest) shift; dest="${1:-}" ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

repo=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
if [ -z "$dest" ]; then
    home="${HOME:-}"
    if [ -z "$home" ] && [ -n "${USERPROFILE:-}" ]; then
        home=$USERPROFILE
    fi
    if [ -z "$home" ]; then
        echo "Cannot detect the home folder; pass --dest DIR" >&2
        exit 1
    fi
    dest="$home/.darkan/scripts"
fi

mkdir -p "$dest"
dest=$(CDPATH= cd -- "$dest" && pwd -P)

if [ "$dest" = "$repo" ]; then
    echo "This repository already is the scripts folder ($dest); nothing to copy."
    exit 0
fi

count=0
for script in "$repo"/*.kts; do
    [ -e "$script" ] || continue
    cp -f "$script" "$dest/"
    count=$((count + 1))
done

libs=0
if [ -d "$repo/lib" ]; then
    mkdir -p "$dest/lib"
    for shared in "$repo"/lib/*.kts; do
        [ -e "$shared" ] || continue
        cp -f "$shared" "$dest/lib/"
        libs=$((libs + 1))
    done
fi

if [ "$clean" -eq 1 ]; then
    for existing in "$dest"/*.kts "$dest"/lib/*.kts; do
        [ -e "$existing" ] || continue
        relative=${existing#"$dest"/}
        if [ ! -e "$repo/$relative" ]; then
            rm -f -- "$existing"
            echo "Removed stale $relative"
        fi
    done
fi

echo "Installed $count script(s) and $libs shared file(s) into $dest"
echo "Press \"Reload scripts\" in the bot sidebar (or restart the client) to load them."
