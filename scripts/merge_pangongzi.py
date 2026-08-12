#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 pangongzi/phone 的 phone.dat（~517k 条号段归属地）并入 refuse-phone 的
app/src/main/assets/phonedata.db。

策略：
  - 解析 pangongzi dat（phone.dat 二进制格式，uint16 记录偏移 + 运营商类型字节），
    运营商类型映射补到 8（中国广电虚拟运营商）。
  - 读取现有 phonedata.db 的 segments 表（hiwjd/phonedata 来源，~231k 条）。
  - 以 prefix 为主键合并：现有先放入，pangongzi 后放（INSERT OR REPLACE 覆盖），
    保证 pangongzi 更新更全的数据优先，且不丢失现有独有的号段。
  - 重新生成同 schema 的 SQLite：segments(prefix, province, city, isp)。
"""
import os
import sqlite3
import struct

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DAT_PATH = os.path.join(SCRIPT_DIR, "phone_pangongzi.dat")
OLD_DB = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "phonedata.db")

# 运营商类型映射（phonedata 的 cardtype 字节），覆盖到 8
CARD_MAP = {
    0: "",
    1: "中国移动",
    2: "中国联通",
    3: "中国电信",
    4: "中国电信虚拟运营商",
    5: "中国联通虚拟运营商",
    6: "中国移动虚拟运营商",
    7: "中国广电",
    8: "中国广电虚拟运营商",
}


def parse_phone_dat(path):
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 8:
        raise ValueError("phone.dat 文件过小或非预期格式")
    first_index_offset = struct.unpack_from("<I", data, 4)[0]
    records = []
    idx = data[first_index_offset:]
    n = len(idx)
    pos = 0
    while pos + 9 <= n:
        prefix = struct.unpack_from("<I", idx, pos)[0]
        rec_off = struct.unpack_from("<H", idx, pos + 4)[0]
        cardtype = idx[pos + 8]
        if rec_off < 8 or rec_off >= len(data):
            pos += 9
            continue
        end = data.index(b"\x00", rec_off)
        rec = data[rec_off:end].decode("utf-8", "ignore")
        parts = rec.split("|")
        if len(parts) >= 4:
            province, city = parts[0], parts[1]
            isp = CARD_MAP.get(cardtype, "")
            records.append((prefix, province, city, isp))
        pos += 9
    return records


def read_existing(db_path):
    if not os.path.exists(db_path):
        return []
    conn = sqlite3.connect(db_path)
    rows = conn.execute("SELECT prefix, province, city, isp FROM segments").fetchall()
    conn.close()
    return [(int(r[0]), r[1], r[2], r[3]) for r in rows]


def build_db(records, out_db):
    tmp = out_db + ".tmp"
    if os.path.exists(tmp):
        os.remove(tmp)
    conn = sqlite3.connect(tmp)
    conn.execute("PRAGMA journal_mode=DELETE")
    conn.execute(
        """CREATE TABLE segments(
            prefix INTEGER PRIMARY KEY,
            province TEXT,
            city TEXT,
            isp TEXT
        )"""
    )
    conn.executemany(
        "INSERT OR REPLACE INTO segments(prefix,province,city,isp) VALUES(?,?,?,?)",
        records,
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_seg_prefix ON segments(prefix)")
    conn.commit()
    cnt = conn.execute("SELECT COUNT(*) FROM segments").fetchone()[0]
    conn.close()
    if os.path.exists(out_db):
        os.remove(out_db)
    os.replace(tmp, out_db)
    return cnt


def main():
    print("[parse] pangongzi dat ...")
    pg = parse_phone_dat(DAT_PATH)
    print(f"[parse] pangongzi 得到 {len(pg)} 条")

    print("[read] 现有 phonedata.db ...")
    old = read_existing(OLD_DB)
    print(f"[read] 现有 {len(old)} 条")

    # 合并：先放现有，再放 pangongzi（覆盖冲突），保证 pangongzi 优先
    merged = {}
    for prefix, prov, city, isp in old:
        merged[prefix] = (prefix, prov, city, isp)
    for prefix, prov, city, isp in pg:
        merged[prefix] = (prefix, prov, city, isp)
    records = list(merged.values())
    print(f"[merge] 合并后 {len(records)} 条")

    cnt = build_db(records, OLD_DB)
    print(f"[build] 写入 {OLD_DB}（{cnt} 条）")

    # 抽样校验
    conn = sqlite3.connect(OLD_DB)
    for test_prefix in (1380013, 1300000, 1700000):
        row = conn.execute(
            "SELECT prefix,province,city,isp FROM segments WHERE prefix=?",
            (test_prefix,),
        ).fetchone()
        print(f"  sample prefix={test_prefix}: {row}")
    conn.close()


if __name__ == "__main__":
    main()
