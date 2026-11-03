#!/usr/bin/env bash
# Render legacy launcher PNGs (API 24-25, no adaptive icons) from the generated SVG.
# Usage: tools/render_legacy_icons.sh   (needs python3 + ImageMagick 7 "magick")
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
python3 -I tools/gen_app_icon.py --preview "$tmp" >/dev/null
magick -background none "$tmp/icon_full.svg" "$tmp/full.png"     # 1080 px = 108 dp canvas
magick "$tmp/full.png" -crop 720x720+180+180 +repage "$tmp/visible.png"  # inner 72 dp
for spec in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  dpi=${spec%%:*}; px=${spec##*:}; dir="app/src/main/res/mipmap-$dpi"
  r=$((px * 22 / 100))
  magick "$tmp/visible.png" -resize "${px}x${px}" \
    \( -size "${px}x${px}" xc:none -fill white -draw "roundrectangle 0,0 $((px-1)),$((px-1)) $r,$r" \) \
    -compose DstIn -composite -quality 100 -define webp:lossless=true "$dir/ic_launcher.webp"
  magick "$tmp/visible.png" -resize "${px}x${px}" \
    \( -size "${px}x${px}" xc:none -fill white -draw "circle $((px/2)),$((px/2)) $((px/2)),0" \) \
    -compose DstIn -composite -quality 100 -define webp:lossless=true "$dir/ic_launcher_round.webp"
done
# Play Store listing icon (512x512, full-bleed square).
magick "$tmp/visible.png" -resize 512x512 app/src/main/ic_launcher-playstore.png
echo "Legacy icons written"
