#!/bin/sh
set -eu

usage() {
    cat <<USAGE
Usage: ./install.sh [--clean] [--dest DIR]

Copies every .kts script from this repository into the folder the Darkan client
scans for scripts, overwriting files of the same name. Press "Reload scripts"
in the bot sidebar afterwards.

  --clean     Also delete .kts files in the destination that are not in this repository
              (private/ is never copied or deleted; it is your own folder)
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
libs=0
while IFS= read -r relative; do
    [ -n "$relative" ] || continue
    target="$dest/$relative"
    mkdir -p "$(dirname -- "$target")"
    cp -f "$repo/$relative" "$target"
    case "$relative" in
        lib/*) libs=$((libs + 1)) ;;
        *) count=$((count + 1)) ;;
    esac
done <<EOF
$(cd "$repo" && find . -name '*.kts' -not -path './.git/*' -not -path './.claude/*' -not -path './private/*' | sed 's|^\./||' | sort)
EOF

if [ "$clean" -eq 1 ]; then
    while IFS= read -r relative; do
        [ -n "$relative" ] || continue
        if [ ! -e "$repo/$relative" ]; then
            rm -f -- "$dest/$relative"
            echo "Removed stale $relative"
        fi
    done <<EOF
$(cd "$dest" && find . -name '*.kts' -not -path './.git/*' -not -path './.claude/*' -not -path './private/*' | sed 's|^\./||' | sort)
EOF
fi

echo "Installed $count script(s) and $libs shared file(s) into $dest"
echo "Press \"Reload scripts\" in the bot sidebar (or restart the client) to load them."
