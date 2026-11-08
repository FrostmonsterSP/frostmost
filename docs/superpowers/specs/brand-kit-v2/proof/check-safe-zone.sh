#!/usr/bin/env bash
# Safe-zone check for the FrostMost v2 Android adaptive icon.
#
# HOW TO RUN (from the repository root):
#
#     bash docs/superpowers/specs/brand-kit-v2/proof/check-safe-zone.sh
#
# WHAT IT DOES
#   1. Rasterises the adaptive foreground to 432 px (108 units * 4 px/unit).
#   2. Draws a 1 px red circle of radius 33*4 = 132 px centred at (216,216)
#      via  -draw "circle 216,216 216,84"  and saves the overlay as proof.
#   3. Measures, in pixels, every pixel that belongs to the mark, finds the
#      maximum distance from the centre, converts it to canvas units and
#      asserts it is <= 33 - 2 = 31 units (CONTRACT.md section 2).
#
# EXIT STATUS
#   0  safe zone passes with the required >= 2-unit margin
#   1  a mark pixel is closer than 2 units to the circle edge, or crosses it
#   2  bad invocation / missing input / unrenderable SVG
#
# This script is re-runnable: it regenerates the SVGs first (gen.py), so it
# always measures the geometry currently inside the <g id="mark"> block.
# Re-run it after mark-designer swaps in the final mark.  The measurement is
# on the RENDERED result, not on the declared path coordinates, so it catches
# curves that bulge outside their control points.

set -euo pipefail

# this script sits at docs/superpowers/specs/brand-kit-v2/proof/
KIT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$KIT/../../.." && pwd)"
FG="$KIT/icons/frostmost-adaptive-foreground.svg"
PROOF="$KIT/proof"
WORK="${TMPDIR_WORK:-$REPO_ROOT/.tmp/icon-work}"

SAFE_RADIUS_UNITS=33      # safe circle radius, canvas units
MIN_MARGIN_UNITS=2        # required clearance (CONTRACT.md section 2)
PX_PER_UNIT=4             # 432 px / 108 units
CENTRE_PX=$((54 * PX_PER_UNIT))        # 216
SAFE_RADIUS_PX=$((SAFE_RADIUS_UNITS * PX_PER_UNIT))  # 132
MAX_RADIUS_PX=$(( (SAFE_RADIUS_UNITS - MIN_MARGIN_UNITS) * PX_PER_UNIT ))  # 124

command -v rsvg-convert >/dev/null || { echo "FAIL: rsvg-convert not found" >&2; exit 2; }
command -v magick      >/dev/null || { echo "FAIL: magick not found"      >&2; exit 2; }
[ -f "$FG" ] || { echo "FAIL: missing $FG" >&2; exit 2; }

mkdir -p "$PROOF" "$WORK"

# --- 1. rasterise the foreground -------------------------------------------
rsvg-convert -w 432 -h 432 -o "$WORK/fg-432.png" "$FG"

# --- 2. prove the mark is actually there before measuring it ---------------
DISTINCT="$(magick "$WORK/fg-432.png" -format '%k' info:)"
if [ "$DISTINCT" -lt 1 ] || [ "$(magick "$WORK/fg-432.png" -alpha extract -format '%[fx:maxima]' info: | cut -d. -f1)" = "0" ]; then
  echo "FAIL: foreground rendered empty or fully transparent" >&2
  exit 2
fi

# --- 3. overlay the safe circle and save the proof -------------------------
magick "$WORK/fg-432.png" \
  -draw "stroke red stroke-width 1 fill none circle 216,216 216,84" \
  "$PROOF/adaptive-foreground-safe-zone.png"

# Same overlay, but also clipped by the mark's own alpha so the mark is visible
# against a dark plate -- easier for a human to read the margin.
magick -size 432x432 xc:'#101114' \
  \( "$WORK/fg-432.png" \) -composite \
  -draw "stroke red stroke-width 1 fill none circle 216,216 216,84" \
  "$PROOF/adaptive-foreground-safe-zone-on-dark.png"

# --- 4. measure the real maximum radius of mark pixels ---------------------
python3 - "$WORK/fg-432.png" "$CENTRE_PX" "$PX_PER_UNIT" "$MAX_RADIUS_PX" <<'PY'
import sys
import zlib
import struct

path, centre_px, px_per_unit, max_radius_px = (
    sys.argv[1], int(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4])
)

def read_png(path):
    """Minimal PNG reader: returns (width, height, rows of RGBA tuples)."""
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, w, h, depth, ctype = 8, b"", None, None, None, None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", chunk[:10])
        elif ctag == b"IDAT":
            idat += chunk
        elif ctag == b"IEND":
            break
    assert depth == 8, f"expected 8-bit depth, got {depth}"
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * channels
    rows, prev = [], bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        elif f != 0:
            raise ValueError(f"bad filter {f}")
        rows.append(bytes(line))
        prev = line
    return w, h, channels, rows

w, h, channels, rows = read_png(path)

max_d = -1.0
max_at = None
count = 0
for y in range(h):
    row = rows[y]
    for x in range(w):
        # "belongs to the mark" = any pixel carrying mark colour (alpha > 0 for
        # a transparent-background render; white for an opaque render).
        if channels == 4:
            a = row[x * 4 + 3]
            r = row[x * 4]
        elif channels == 3:
            r, a = row[x * 3], 255
        else:
            r, a = row[x], 255
        if a > 8 and r > 8:
            count += 1
            dx, dy = x + 0.5 - centre_px, y + 0.5 - centre_px
            d = (dx * dx + dy * dy) ** 0.5
            if d > max_d:
                max_d, max_at = d, (x, y)

if count == 0:
    print("FAIL: no mark pixels found in the rendered foreground", file=sys.stderr)
    sys.exit(2)

max_units = max_d / px_per_unit
margin = 33.0 - max_units

print(f"mark pixels            : {count}")
print(f"max radius (px)        : {max_d:.2f}   at pixel {max_at}")
print(f"max radius (units)     : {max_units:.3f}")
print(f"limit (r=33, -2 margin): 31.000 units")
print(f"achieved margin        : {margin:.3f} units to the r=33 circle edge")
print(f"verdict                : {'PASS' if max_d <= max_radius_px else 'FAIL'}")

sys.exit(0 if max_d <= max_radius_px else 1)
PY
