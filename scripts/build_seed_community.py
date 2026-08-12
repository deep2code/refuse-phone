#!/usr/bin/env python3
"""
Normalize community spam/blacklist sources into the app's seed CSV format.

Output format (no header, one row per number):
    description,value

where `value` is a plaintext number already in the E.164 form an *incoming*
call would produce after SpamHashRepository.normalizeToE164():
  - US numbers "(559) 214-1698"  -> "+15592141698"
  - intl numbers "+22375888"      -> "+22375888"   (kept as-is)

Storing them in E.164 guarantees the hash computed at seed time equals the
hash computed for a real incoming call -> a match.
"""
import re
import sys

NATHANU98 = "/tmp/spamdata/nathanu98.txt"      # (AREA) PREFIX-SUFFIX  (US)
SHALOM = "/tmp/spamdata/shalom.txt"             # +<digits>            (intl)
OUT = "/tmp/spamdata/seed_community.csv"

RE_NATHANU = re.compile(r"\((\d{3})\)\s*(\d{3})-(\d{4})")
RE_SHALOM = re.compile(r"\+(\d{6,15})")         # valid E.164-ish length

def read_lines(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            yield line.strip()

def main():
    rows = {}  # e164 -> description

    n_in = n_ok = n_skip = 0
    for line in read_lines(NATHANU98):
        if not line:
            continue
        n_in += 1
        m = RE_NATHANU.match(line)
        if not m:
            n_skip += 1
            continue
        e164 = "+1" + m.group(1) + m.group(2) + m.group(3)
        rows.setdefault(e164, "ScammerPhoneNumbers")
        n_ok += 1
    print(f"nathanu98: in={n_in} ok={n_ok} skip={n_skip}")

    s_in = s_ok = s_skip = 0
    for line in read_lines(SHALOM):
        if not line:
            continue
        s_in += 1
        m = RE_SHALOM.match(line)
        if not m:
            s_skip += 1
            continue
        e164 = "+" + m.group(1)
        # intl source wins on collision; otherwise keep first
        rows.setdefault(e164, "AI-Number-Blocklist-skip")
        # prefer explicit source label if not yet set
        if e164 not in rows or rows[e164].startswith("AI-Number-Blocklist-skip"):
            rows[e164] = "AI-Number-Blocklist"
        s_ok += 1
    print(f"shalom:    in={s_in} ok={s_ok} skip={s_skip}")

    # final pass to enforce correct labels (setdefault above is order-dependent)
    # Re-derive cleanly: union with explicit sources.
    out_rows = []
    seen = set()
    # nathanu98
    for line in read_lines(NATHANU98):
        if not line:
            continue
        m = RE_NATHANU.match(line)
        if not m:
            continue
        e164 = "+1" + m.group(1) + m.group(2) + m.group(3)
        if e164 in seen:
            continue
        seen.add(e164)
        out_rows.append(("ScammerPhoneNumbers", e164))
    # shalom
    for line in read_lines(SHALOM):
        if not line:
            continue
        m = RE_SHALOM.match(line)
        if not m:
            continue
        e164 = "+" + m.group(1)
        if e164 in seen:
            continue
        seen.add(e164)
        out_rows.append(("AI-Number-Blocklist", e164))

    with open(OUT, "w", encoding="utf-8") as f:
        for desc, val in out_rows:
            f.write(f"{desc},{val}\n")

    print(f"TOTAL unique numbers written: {len(out_rows)}")
    print(f"  ScammerPhoneNumbers : {sum(1 for d,_ in out_rows if d=='ScammerPhoneNumbers')}")
    print(f"  AI-Number-Blocklist : {sum(1 for d,_ in out_rows if d=='AI-Number-Blocklist')}")
    print(f"-> {OUT}")

if __name__ == "__main__":
    main()
