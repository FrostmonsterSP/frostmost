# Android icon proofs — FrostMost v2

Scope: the three Android icon SVGs and their rendered proofs.
Owner: icon-engineer. The symbol and wordmark files belong to mark-designer.

## Files

| File | What |
|---|---|
| `icons/frostmost-adaptive-foreground.svg` | 108-unit adaptive canvas, 432×432, mark only in flat `#FFFFFF` |
| `icons/frostmost-adaptive-background.svg` | 108-unit adaptive canvas, 432×432, flat `#3D57B2` rect, square corners |
| `icons/frostmost-app-icon.svg` | 1024×1024, rounded square `#3D57B2`, mark in flat `#FFFFFF` |

All three carry the mark in exactly one place: a commented `<g id="mark">`
block. Swapping the two `<path>` values inside it swaps the mark everywhere.

## Reproducing

```sh
# 1. regenerate the three SVGs (only needed if the mark changes)
python3 .tmp/icon-work/gen.py

# 2. measure the safe zone and rebuild the safe-zone proofs
bash docs/superpowers/specs/brand-kit-v2/proof/check-safe-zone.sh

# 3. render the size proofs
K=docs/superpowers/specs/brand-kit-v2
for s in 512 96 48 24; do
  rsvg-convert -w $s -h $s -o "$K/proof/app-icon-${s}.png" "$K/icons/frostmost-app-icon.svg"
done
magick "$K/proof/app-icon-24.png" -filter point -resize 800% "$K/proof/app-icon-24-x8.png"
magick "$K/proof/app-icon-48.png" -filter point -resize 800% "$K/proof/app-icon-48-x8.png"

# 4. adaptive composite, plain and under a circular launcher mask
rsvg-convert -w 432 -h 432 -o .tmp/icon-work/bg432.png "$K/icons/frostmost-adaptive-background.svg"
rsvg-convert -w 432 -h 432 -o .tmp/icon-work/fg432.png "$K/icons/frostmost-adaptive-foreground.svg"
magick .tmp/icon-work/bg432.png .tmp/icon-work/fg432.png -composite \
  "$K/proof/adaptive-composite-432.png"
magick .tmp/icon-work/bg432.png .tmp/icon-work/fg432.png -composite \
  \( -size 432x432 xc:none -draw "fill white stroke none circle 216,216 348,216" \) \
  -compose DstIn -composite "$K/proof/adaptive-composite-round-mask-432.png"
```

## Measured safe-zone result

`adaptive-safe-zone-report.txt` holds the machine output of
`check-safe-zone.sh`; the script exits non-zero if the mark leaves the zone.

- Canvas 108×108, safe circle r = 33 at (54, 54), rasterised at 4 px/unit.
- **Max radius of mark pixels: 30.784 units** (123.14 px).
- Limit with the required 2-unit margin: 31.000 units (124 px).
- **Margin to the r = 33 circle edge: 2.216 units.**
- Verdict: PASS. No mark pixel touches or crosses the circle.

The measurement runs on the rasterised PNG, not on the declared path
coordinates, so a curve that bulges outside its control points cannot slip
through.

## Honest legibility verdict

| Size | Reads? |
|---|---|
| 512 / 96 | Yes. Peak and arch both crisp, the gap between them is unambiguous. |
| 48 | Yes. Both forms distinct, gap clearly visible. |
| 24 | **Marginal.** The arch reads. The peak survives as a solid stroke, but the peak↔arch gap compresses to about one pixel-ish and anti-aliasing softens it into a faint bridge. It is better than v1 (which fused into an "A"), and it is not yet as clean as the brief's §3.1 "reads at 24 px" bar wants. |

The gap is the limiting factor at 24 px: the contract fixes the peak base at
y≈33 and the arch outer top at y=38 (arch outer diameter is capped by the peak
base per §1.2), so the true gap is 5 units of the 100-unit mark canvas. Enlarging
it means changing the symbol proportions, which belongs to mark-designer, not
here.

## Note on optical sizing

The contract measures the safe zone at the bbox corner, but the safe zone is a
circle and the corner carries no ink. Scaling by the bbox corner would have
rendered the mark visibly smaller than the safe zone permits. Instead the mark is
sized so its **rendered** extreme sits at 31 units, which puts the bbox corner at
32.185 units — still inside the r = 33 circle. Both facts are verified: the
raster check above, and a `bbox_check()` assertion in the generator that fails the
build if the corner ever exceeds 33. Height of the mark as drawn is 56.9% of the
foreground canvas; before this change it was 50.6%.
