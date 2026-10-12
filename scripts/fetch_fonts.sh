#!/usr/bin/env bash
# Downloads the open-source (OFL) fonts used by the three designs into res/font.
set -euo pipefail
dir="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
mkdir -p "$dir"
base="https://raw.githubusercontent.com/google/fonts/main/ofl"
fetch() { [ -s "$dir/$2" ] || curl -fsSL "$base/$1" -o "$dir/$2"; }
fetch "plusjakartasans/PlusJakartaSans%5Bwght%5D.ttf" plus_jakarta_sans.ttf
fetch "spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf" space_grotesk.ttf
fetch "jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf" jetbrains_mono.ttf
fetch "bricolagegrotesque/BricolageGrotesque%5Bopsz,wdth,wght%5D.ttf" bricolage_grotesque.ttf
fetch "outfit/Outfit%5Bwght%5D.ttf" outfit.ttf
ls -la "$dir"
