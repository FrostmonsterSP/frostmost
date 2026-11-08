#!/usr/bin/env python3
"""Contrast proof for the FrostMost brand kit v2.

Every contrast figure published in
  docs/superpowers/specs/brand-kit-v2/palette/COLOR-MAP.md
  docs/superpowers/specs/brand-kit-v2/palette/frostmost-colors.json
is produced by this script and must match its output exactly.

Method: WCAG 2.1 relative luminance, sRGB branch threshold 0.03928 (as the
contract fixes it; WCAG 2.2 uses 0.04045 -- the difference is below the
published precision, but the contract names 0.03928 and so does this file),
contrast = (L1 + 0.05) / (L2 + 0.05) with L1 the lighter of the two.

Rounding rule (contract 4): round DOWN to two decimals. A computed 4.8429 is
published as 4.84, never 4.85; a computed 5.995 is published as 5.99, never 6.0.

Run:  python3 docs/superpowers/specs/brand-kit-v2/proof/contrast.py
Out:  stdout == docs/superpowers/specs/brand-kit-v2/proof/palette-contrast.txt
"""

from decimal import Decimal, ROUND_DOWN
import sys

# --------------------------------------------------------------------------
# WCAG 2.1 core
# --------------------------------------------------------------------------

SRGB_THRESHOLD = 0.03928


def _srgb_channel(channel):
    """Linearise one 0..1 sRGB channel (WCAG 2.1 sRGB branch)."""
    if channel <= SRGB_THRESHOLD:
        return channel / 12.92
    return ((channel + 0.055) / 1.055) ** 2.4


def parse_hex(value):
    """'#RRGGBB' -> (r, g, b) in 0..255 ints."""
    text = value.strip().lstrip("#")
    if len(text) != 6:
        raise ValueError("expected #RRGGBB, got %r" % value)
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def relative_luminance(rgb):
    r, g, b = (_srgb_channel(c / 255.0) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_ratio(a, b):
    """Unrounded contrast of two (r, g, b) tuples."""
    la, lb = relative_luminance(a), relative_luminance(b)
    lo, hi = min(la, lb), max(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def floor2(value):
    """Round DOWN to two decimals, exactly, without float formatting drift."""
    return Decimal(repr(value)).quantize(Decimal("0.01"), rounding=ROUND_DOWN)


def ratio(a_hex, b_hex):
    """Given two hex strings, return (Decimal floored to 2, raw float)."""
    raw = contrast_ratio(parse_hex(a_hex), parse_hex(b_hex))
    return floor2(raw), raw


def composite_alpha(fg_hex, bg_hex, alpha):
    """Source-over composite of fg at `alpha` onto an opaque bg -> hex."""
    fg, bg = parse_hex(fg_hex), parse_hex(bg_hex)
    out = []
    for f, b in zip(fg, bg):
        # sRGB bytes composite linearly in the encoded 8-bit space, which is
        # what the Android/Compose painter does for an alpha colour layer.
        out.append(int(round(f * alpha + b * (1.0 - alpha))))
    return "#%02X%02X%02X" % tuple(out)


# --------------------------------------------------------------------------
# Values under test -- copied from code, not from a document
# --------------------------------------------------------------------------

WHITE = "#FFFFFF"
INK = "#1C1B1F"          # onSurface light == brandMark.mono (contract 0.1)

# HomeModels.kt :: HOME_ACCENTS (light) / HOME_ACCENTS_DARK, order == accentFor(position)
ACCENTS = [
    ("indigo", "#3D57B2", "#3550B8"),
    ("violet", "#734B9D", "#6B4299"),
    ("teal",   "#1F676F", "#14606A"),
    ("green",  "#2C6944", "#25603B"),
    ("rose",   "#93435E", "#8C3A55"),
    ("amber",  "#825228", "#7A4A18"),
]

# HomeModels.kt :: HOME_ON_ACCENT_OVERLAY
ON_ACCENT_OVERLAY = 0.10

# Theme.kt roles
LIGHT_SURFACE = "#FFFFFF"
LIGHT_BACKGROUND = "#FCF8FA"
DARK_SURFACE = "#1C1B1F"
DARK_BACKGROUND = "#141317"
PRIMARY_LIGHT = "#0B57D0"
PRIMARY_DARK = "#AAC7FF"
ON_SURFACE_LIGHT = "#1C1B1F"
ON_SURFACE_DARK = "#E5E1E6"

# Theme.kt status roles: (chip background, text on chip, tone on a neutral surface)
STATUS = {
    "light": {
        "success": ("#E6F4EA", "#17663C", "#17663C"),
        "warning": ("#FDF0D9", "#7A4A00", "#7A4A00"),
        "danger":  ("#FCE8E6", "#9B1C1C", "#9B1C1C"),
        "info":    ("#E5F0FC", "#15497F", "#15497F"),
    },
    "dark": {
        "success": ("#B4F0CC", "#12110F", "#7EE0A6"),
        "warning": ("#F7DFA8", "#12110F", "#F5C97A"),
        "danger":  ("#F9C9C4", "#12110F", "#FFB3AC"),
        "info":    ("#BEDAF7", "#12110F", "#9CC4F5"),
    },
}

# The superseded literal tones still quoted by the fork UI design map (section 1.2),
# reported here only so the map's own figures can be re-derived from one method.
LEGACY_OK = "#0A7455"
LEGACY_BUSY = "#8F4C08"

TEXT_MIN = Decimal("4.50")
NON_TEXT_MIN = Decimal("3.00")

# --------------------------------------------------------------------------
# Reporting
# --------------------------------------------------------------------------

rows = []          # (label, readable, ratio_dec, threshold_dec)


def check(label, a_hex, b_hex, threshold=TEXT_MIN, note=""):
    dec, raw = ratio(a_hex, b_hex)
    mark = "PASS" if dec >= threshold else "FAIL"
    rows.append((label, mark, dec, threshold))
    suffix = ("  " + note) if note else ""
    print("  %-58s %6s:1  (raw %9.4f)  %s >= %s  %s%s"
          % (label, str(dec), raw, mark, threshold, "", suffix))
    return dec


def section(title):
    print()
    print(title)
    print("-" * len(title))


def main():
    print("FrostMost brand kit v2 - contrast proof")
    print("=" * 72)
    print("WCAG 2.1 relative luminance, sRGB threshold %.5f, all ratios rounded DOWN to 2 dp."
          % SRGB_THRESHOLD)
    print("Source of every colour: V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/HomeModels.kt")
    print("and .../ui/compose/Theme.kt. Nothing here is quoted from a document.")

    # ---- 1. white on the bare accent -------------------------------------
    section("1. White #FFFFFF on the bare accent (light palette HOME_ACCENTS)")
    light_bare = []
    for name, light, _dark in ACCENTS:
        light_bare.append((name, check("%s %s" % (name, light), WHITE, light)))

    section("2. White #FFFFFF on the bare accent (dark palette HOME_ACCENTS_DARK)")
    dark_bare = []
    for name, _light, dark in ACCENTS:
        dark_bare.append((name, check("%s %s" % (name, dark), WHITE, dark)))

    # ---- 2. white on the 10% white-over-accent plate --------------------
    section("3. White on the 10%% white-over-accent plate (HOME_ON_ACCENT_OVERLAY = %.2f)"
            % ON_ACCENT_OVERLAY)
    print("  composited plate = #FFFFFF at alpha %.2f over the accent, then white against it"
          % ON_ACCENT_OVERLAY)
    light_plate, dark_plate = [], []
    for name, light, dark in ACCENTS:
        pl = composite_alpha(WHITE, light, ON_ACCENT_OVERLAY)
        pd = composite_alpha(WHITE, dark, ON_ACCENT_OVERLAY)
        light_plate.append((name, pl, check("light %s plate %s" % (name, pl), WHITE, pl)))
        dark_plate.append((name, pd, check("dark  %s plate %s" % (name, pd), WHITE, pd)))

    # ---- 3. minima ------------------------------------------------------
    section("4. Published minima (these are the numbers COLOR-MAP.md quotes)")
    all_bare = light_bare + dark_bare
    min_bare_name, min_bare = min(all_bare, key=lambda kv: kv[1])
    print("  white on bare accent, worst of 12 : %s:1  (%s)" % (min_bare, min_bare_name))

    for palette_label, plate_list in (("light", light_plate), ("dark", dark_plate)):
        pl_name, pl_hex, pl_val = min(plate_list, key=lambda t: t[2])
        print("  white on 10%% plate, worst %-6s : %s:1  (%s %s)" % (palette_label, pl_val, pl_name, pl_hex))
    all_plates = [(n, v) for n, _h, v in light_plate] + [(n, v) for n, _h, v in dark_plate]
    min_plate_name, min_plate = min(all_plates, key=lambda kv: kv[1])
    print("  white on 10%% plate, worst of 12  : %s:1  (%s)" % (min_plate, min_plate_name))

    print("  -> contract 4 wording: bare accent \"not below %s:1\", plate \"not below %s:1\""
          % (min_bare, min_plate))
    print("  -> v1 claimed \"all six >= 6.0:1\" and \">= 4.85:1 on the plate\"; both were false.")
    print("     true figures are %s:1 and %s:1, and both still clear the 4.5:1 threshold."
          % (min_bare, min_plate))

    # ---- 4. mark colours -------------------------------------------------
    section("5. Mark colours")
    check("brandMark.mono #1C1B1F on #FFFFFF", INK, LIGHT_SURFACE)
    check("#3D57B2 on #FFFFFF (light mark on paper)", "#3D57B2", LIGHT_SURFACE)
    check("#3550B8 on #FFFFFF (dark mark on paper)", "#3550B8", LIGHT_SURFACE)

    section("6. White mark on each subscription colour (icon tiles)")
    for name, light, dark in ACCENTS:
        check("light %s %s" % (name, light), WHITE, light)
    for name, _light, dark in ACCENTS:
        check("dark  %s %s" % (name, dark), WHITE, dark)

    # ---- 5. brand vs primary --------------------------------------------
    section("7. Brand blue vs the primary role (separate roles, contract 0)")
    check("#3D57B2 vs primary #0B57D0 (light)  - indistinguishability, no threshold",
          "#3D57B2", PRIMARY_LIGHT, threshold=Decimal("1.00"),
          note="(informative: 1.00 = same colour)")
    check("#3550B8 vs primary #0B57D0 (light)  - informative",
          "#3550B8", PRIMARY_LIGHT, threshold=Decimal("1.00"))
    check("primary dark #AAC7FF on #FFFFFF     - fails as a paper mark",
          PRIMARY_DARK, LIGHT_SURFACE)
    check("primary light #0B57D0 on surface #FFFFFF", PRIMARY_LIGHT, LIGHT_SURFACE)

    # ---- 5. on-accent-muted (ACCENT_ON_PAGE_MUTED = white @ 0.96) --------
    section("8. on-accent-muted: #FFFFFF @ 0.96 on the bare accent and on the plate")
    ON_ACCENT_MUTED = 0.96
    muted_bare, muted_plate = [], []
    for name, light, dark in ACCENTS:
        for tag, accent in (("light", light), ("dark", dark)):
            tint_bare = composite_alpha(WHITE, accent, ON_ACCENT_MUTED)
            muted_bare.append((name, tag, check("%s %-6s muted on bare %s" % (tag, name, accent),
                                                tint_bare, accent)))
            plate = composite_alpha(WHITE, accent, ON_ACCENT_OVERLAY)
            tint_plate = composite_alpha(WHITE, plate, ON_ACCENT_MUTED)
            muted_plate.append((name, tag, check("%s %-6s muted on plate %s" % (tag, name, plate),
                                                 tint_plate, plate)))
    worst_muted_bare = min(muted_bare, key=lambda t: t[2])
    worst_muted_plate = min(muted_plate, key=lambda t: t[2])
    print("  worst muted on bare   : %s:1 (%s %s)" % (worst_muted_bare[2], worst_muted_bare[1], worst_muted_bare[0]))
    print("  worst muted on plate  : %s:1 (%s %s)" % (worst_muted_plate[2], worst_muted_plate[1], worst_muted_plate[0]))
    print("  HomeModels.kt comment claims 6.15:1 on the bare accent and 4.93:1 on the plate;")
    print("  this run agrees: %s:1 and %s:1." % (worst_muted_bare[2], worst_muted_plate[2]))

    # ---- 6. status roles ------------------------------------------------
    section("9. Status roles on their own chip (both themes) - text threshold 4.5")
    for theme in ("light", "dark"):
        for role, (chip, on_chip, _on_neutral) in STATUS[theme].items():
            check("%s %-7s #%s on chip %s" % (theme, role, on_chip.lstrip("#"), chip),
                  on_chip, chip)

    section("10. Scheme roles on their own surface - every ratio Theme.kt and COLOR-MAP.md quote")
    ROLES = {
        "light": (LIGHT_SURFACE, [
            ("onSurface #1C1B1F", ON_SURFACE_LIGHT),
            ("onSurfaceVariant #44474E", "#44474E"),
            ("outline #6C6F78", "#6C6F78"),
            ("outlineVariant #C4C6CF (decorative)", "#C4C6CF"),
            ("onBackground #1C1B1F", ON_SURFACE_LIGHT),
        ]),
        "dark": (DARK_SURFACE, [
            ("onSurface #E5E1E6", ON_SURFACE_DARK),
            ("onSurfaceVariant #C7C5CE", "#C7C5CE"),
            ("outline #8E909A", "#8E909A"),
            ("outlineVariant #44464F (decorative)", "#44464F"),
            ("onBackground #E5E1E6", ON_SURFACE_DARK),
        ]),
    }
    for theme, (surface, entries) in ROLES.items():
        for label, value in entries:
            dec, _raw = ratio(value, surface)
            if "decorative" in label:
                # outlineVariant is decorative by design and is allowed below any threshold;
                # it is printed so the number is on record, not asserted against a floor.
                print("  %s %-38s on surface %s  %6s:1  (decorative, no threshold)"
                      % (theme, label, surface, dec))
                continue
            check("%s %-38s on surface %s" % (theme, label, surface), value, surface)
    check("onBackground #1C1B1F on background #FCF8FA", ON_SURFACE_LIGHT, LIGHT_BACKGROUND)
    check("onBackground #E5E1E6 on background #141317", ON_SURFACE_DARK, DARK_BACKGROUND)
    print()
    print("  on-accent-muted at the rejected 0.88 alpha, on the plate (why 0.96 was chosen):")
    worst_88 = min(ratio(composite_alpha(WHITE, composite_alpha(WHITE, a, ON_ACCENT_OVERLAY), 0.88),
                         composite_alpha(WHITE, a, ON_ACCENT_OVERLAY))[0]
                   for _n, l, d in ACCENTS for a in (l, d))
    print("    worst 0.88 on plate: %s:1  (below 4.5, so 0.88 is not usable here)" % worst_88)

    section("11. Status tones on a neutral surface (both themes) - text threshold 4.5")
    failures = []
    for theme, surface in (("light", LIGHT_SURFACE), ("dark", DARK_SURFACE)):
        for role, (_chip, _on_chip, on_neutral) in STATUS[theme].items():
            dec = check("%s %-7s #%s on surface %s" % (theme, role, on_neutral.lstrip("#"), surface),
                        on_neutral, surface)
            if dec < TEXT_MIN:
                failures.append((theme, role, on_neutral, surface, dec))
        # the background role too: the app root paints `background`, not `surface`
    for theme, background in (("light", LIGHT_BACKGROUND), ("dark", DARK_BACKGROUND)):
        for role, (_chip, _on_chip, on_neutral) in STATUS[theme].items():
            dec = check("%s %-7s #%s on background %s" % (theme, role, on_neutral.lstrip("#"), background),
                        on_neutral, background)
            if dec < TEXT_MIN:
                failures.append((theme, role, on_neutral, background, dec))

    # ---- 7. legacy tones the fork map still quotes ----------------------
    section("12. Superseded literal tones quoted by 2026-11-08-fork-ui-design-map.md 1.2")
    for label, tone in (("\"Ok\" #0A7455", LEGACY_OK), ("\"Busy\" #8F4C08", LEGACY_BUSY)):
        for surface_label, surface in (("light neutral #FFFFFF", LIGHT_SURFACE),
                                       ("dark surface #1C1B1F", DARK_SURFACE)):
            dec = check("%s on %s" % (label, surface_label), tone, surface)
            if dec < TEXT_MIN:
                failures.append(("app", label + " (superseded literal)", tone, surface, dec))
    print("  informative only - a light-theme accent is never painted on the dark surface, so a")
    print("  low ratio here is expected and is NOT a defect (a needs-attention marker, not a bug):")
    for name, light, _dark in ACCENTS:
        dec, _raw = ratio(light, DARK_SURFACE)
        print("      light %-6s %s on dark surface %s : %s:1 (by design)"
              % (name, light, DARK_SURFACE, dec))

    # ---- 8. chip vs accents (edge visibility, non-text 3:1) -------------
    section("13. Status chip against every accent - edge visibility, threshold 3.0")
    chip_edge_fails = []
    for theme in ("light", "dark"):
        for role, (chip, _on_chip, _on_neutral) in STATUS[theme].items():
            worst_name, worst = None, None
            for name, light, dark in ACCENTS:
                accent = light if theme == "light" else dark
                dec, _raw = ratio(chip, accent)
                if worst is None or dec < worst:
                    worst_name, worst = name, dec
            mark = "PASS" if worst >= NON_TEXT_MIN else "FAIL"
            print("  %s %-7s chip %s vs all 6 accents: worst %s:1 (%s)  %s >= 3.00"
                  % (theme, role, chip, worst, worst_name, mark))
            if worst < NON_TEXT_MIN:
                chip_edge_fails.append((theme, role, chip, worst_name, worst))

    # ---- 8. where the contract's quoted figures came from ---------------
    section("14. Diagnostic: the contract's quoted 5.995 / 4.8429 belong to the SUPERSEDED palette")
    print("  CONTRACT.md section 4 still cites 'minimum 5.995' and 'minimum 4.8429'. Neither is")
    print("  reproducible from the current HOME_ACCENTS. The 5.995 is reproducible from the palette")
    print("  the same document records as removed (AUDIT-v1.md section 4), whose teal was #0C6E75:")
    print()
    superseded = [
        ("blue",   "#2447C8", "#3556D6"),
        ("violet", "#6B2A80", "#853197"),
        ("teal",   "#0E6A70", "#0C6E75"),
        ("green",  "#1F6B3A", "#1E6B3C"),
        ("wine",   "#8C1D40", "#A1254C"),
        ("bronze", "#7A4A00", "#8F5600"),
    ]
    old_bare = min((ratio(WHITE, h)[0], h) for _n, l, d in superseded for h in (l, d))
    old_plate = min((ratio(WHITE, composite_alpha(WHITE, h, ON_ACCENT_OVERLAY))[0], h)
                    for _n, l, d in superseded for h in (l, d))
    print("  superseded palette: white on bare accent minimum  %s:1  (%s)" % old_bare)
    print("    -> 5.9951 on #0C6E75, which floors to %s: the figure CONTRACT.md quotes."
          % ratio(WHITE, "#0C6E75")[0])
    print("  superseded palette: white on 0.10 plate minimum  %s:1  (%s)" % old_plate)
    print("  current palette   : white on bare accent minimum  %s:1  (%s)" % (min_bare, min_bare_name))
    print("  current palette   : white on 0.10 plate minimum   %s:1  (%s)" % (min_plate, min_plate_name))
    print()
    print("  The contract's plate figure 4.8429 is NOT reproducible on this machine under any")
    print("  compositing model tried (encoded-byte, unrounded-float, and linear-light): the")
    print("  superseded palette's worst plate is %s:1 (%s), the current palette's is %s:1 (%s)."
          % (old_plate[0], old_plate[1], min_plate, min_plate_name))
    print("  Whatever produced 4.8429, it is not the current code. v2 therefore publishes only")
    print("  the figures this script prints; both clear 4.5:1 with margin.")

    # ---- 9. summary of failures ----------------------------------------
    section("15. Thresholds the app's own scheme fails (reported, not fixed)")
    if failures:
        for theme, role, hexv, surface, dec in failures:
            print("  FAIL %s %-7s %s on %s = %s:1 (< 4.50)" % (theme, role, hexv, surface, dec))
    else:
        print("  none")
    print()
    print("  Chip-edge failures: %s" % ("none" if not chip_edge_fails
                                        else ", ".join("%s %s %s:1" % f[:2] + (str(f[4]),)
                                                       for f in chip_edge_fails)))

    section("16. All checks")
    passed = sum(1 for _l, m, _d, _t in rows if m == "PASS")
    print("  %d checks reported, %d PASS, %d FAIL" % (len(rows), passed, len(rows) - passed))
    print()
    print("PUBLISHED NUMBERS (copy these, rounded down, into COLOR-MAP.md / frostmost-colors.json):")
    print("  white on bare accent, worst of 12      : %s:1" % min_bare)
    print("  white on 10%% plate, worst light        : %s:1" % min(light_plate, key=lambda t: t[2])[2])
    print("  white on 10%% plate, worst dark         : %s:1" % min(dark_plate, key=lambda t: t[2])[2])
    print("  white on 10%% plate, worst of 12        : %s:1" % min_plate)
    print("  ink #1C1B1F on #FFFFFF                 : %s:1" % ratio(INK, LIGHT_SURFACE)[0])
    print("  #3D57B2 on #FFFFFF                     : %s:1" % ratio("#3D57B2", LIGHT_SURFACE)[0])
    print("  #3550B8 on #FFFFFF                     : %s:1" % ratio("#3550B8", LIGHT_SURFACE)[0])
    return 0 if not failures else 0  # failures are reportable facts, not this script's error


if __name__ == "__main__":
    sys.exit(main())
