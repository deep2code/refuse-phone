#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「手机号归属地」离线库 phonedata.db（供 refuse-phone 的 PhoneAttributionRepository 使用）。

数据源（任选其一）：
  1. 默认：xluohome/phonedata（hiwjd fork）的 phone.dat —— 号段级归属地（省/市/运营商）
  2. --csv path.csv：phone-segment 风格 CSV，列：prefix,province,city,isp
  3. --dat path.dat：本地已下载的 phone.dat（跳过下载）

phone.dat 二进制格式（xluohome/phonedata 规范）：
  - 头部 8 字节：4 字节版本号（如 "2511"=25年11月）+ 4 字节首个索引区偏移（uint32 LE）
  - 记录区（从偏移 8 开始）：每条记录为 "省份|城市|邮编|长途区号\\0"，以 \\0 结束
  - 索引区（从首个索引区偏移开始）：每条 9 字节
        [0:4]  手机号前 7 位（uint32 LE 整数）
        [4:6]  记录区绝对文件偏移（uint16 LE）
        [6:9]  保留 2 字节 + 1 字节运营商类型（1=移动 2=联通 3=电信 ...）

输出：app/src/main/assets/phonedata.db
  - SQLite，表 segments(prefix INTEGER PRIMARY KEY, province TEXT, city TEXT, isp TEXT)
  - 已建前缀索引，支持按前 7 位号段快速匹配
  - App 首次启动会把它从 assets 拷贝到 databases 目录并只读查询

用法示例：
  python3 scripts/fetch_phonedata.py                  # 下载 phone.dat 并生成
  python3 scripts/fetch_phonedata.py --url <URL>      # 指定 phone.dat 下载地址
  python3 scripts/fetch_phonedata.py --dat phone.dat  # 用本地 dat
  python3 scripts/fetch_phonedata.py --csv phones.csv # 用本地 CSV
"""
import argparse
import os
import sqlite3
import struct
import sys
import urllib.request

DEFAULT_URL = "https://raw.githubusercontent.com/hiwjd/phonedata/master/phone.dat"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DB = os.path.normpath(
    os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "phonedata.db")
)


def download(url: str, dest: str) -> None:
    print(f"[download] {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    downloaded = 0
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            downloaded += len(chunk)
    print(f"[download] 已保存 {downloaded} 字节 -> {dest}")


def parse_phone_dat(path: str):
    """解析 xluohome/phonedata（hiwjd fork）的 phone.dat 二进制格式。

    见模块 docstring 中的格式说明：头部 8 字节，记录区从偏移 8 开始，
    索引区每条 9 字节（4 字节号段 + 2 字节记录偏移 + 2 字节保留 + 1 字节运营商类型）。
    """
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 8:
        raise ValueError("phone.dat 文件过小或非预期格式")

    first_index_offset = struct.unpack_from("<I", data, 4)[0]

    # 运营商类型映射（phonedata 的 cardtype 字节）
    card_map = {
        0: "",
        1: "中国移动",
        2: "中国联通",
        3: "中国电信",
        4: "中国电信虚拟运营商",
        5: "中国联通虚拟运营商",
        6: "中国移动虚拟运营商",
        7: "中国广电",
    }

    records = []
    idx = data[first_index_offset:]
    n = len(idx)
    pos = 0
    while pos + 9 <= n:
        prefix = struct.unpack_from("<I", idx, pos)[0]        # 手机号前 7 位（uint32 LE）
        rec_off = struct.unpack_from("<H", idx, pos + 4)[0]   # 记录区绝对文件偏移（uint16 LE）
        cardtype = idx[pos + 8]                               # 运营商类型
        if rec_off < 8 or rec_off >= len(data):
            pos += 9
            continue
        end = data.index(b"\x00", rec_off)
        rec = data[rec_off:end].decode("utf-8", "ignore")
        parts = rec.split("|")
        if len(parts) >= 4:
            province, city = parts[0], parts[1]
            isp = card_map.get(cardtype, "")
            records.append((prefix, province, city, isp))
        pos += 9
    print(f"[parse] phone.dat 解析得到 {len(records)} 条记录")
    return records


def parse_csv(path: str):
    records = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            cols = line.split(",")
            if len(cols) < 4:
                continue
            try:
                prefix = int(cols[0].strip())
            except ValueError:
                continue
            records.append((prefix, cols[1].strip(), cols[2].strip(), cols[3].strip()))
    print(f"[parse] csv 解析得到 {len(records)} 条记录")
    return records


def build_db(records, out_db: str) -> None:
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
    print(f"[build] 写入 {out_db}（{cnt} 条，前缀索引已建）")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default=DEFAULT_URL, help="phone.dat 下载地址")
    ap.add_argument("--csv", help="从本地 CSV 生成（列：prefix,province,city,isp）")
    ap.add_argument("--dat", help="本地 phone.dat 路径（跳过下载）")
    ap.add_argument("--out", default=OUT_DB)
    args = ap.parse_args()

    os.makedirs(os.path.dirname(args.out), exist_ok=True)

    if args.csv:
        records = parse_csv(args.csv)
    else:
        dat_path = args.dat
        if not dat_path:
            dat_path = os.path.join(SCRIPT_DIR, "phone.dat")
            try:
                download(args.url, dat_path)
            except Exception as e:  # noqa: BLE001
                print(f"[error] 下载 phone.dat 失败：{e}")
                print("请手动下载 phone.dat 放到 scripts/phone.dat 后重试，或改用 --csv。")
                sys.exit(1)
        records = parse_phone_dat(dat_path)

    if not records:
        print("[error] 未解析到任何记录")
        sys.exit(1)
    build_db(records, args.out)
    print("[done] 生成完成。下一次构建 APK 时 phonedata.db 会作为 asset 打包进应用。")


if __name__ == "__main__":
    main()
