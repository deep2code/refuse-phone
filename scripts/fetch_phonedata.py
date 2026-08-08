#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成「手机号归属地」离线库 phonedata.db（供 refuse-phone 的 PhoneAttributionRepository 使用）。

数据源（任选其一）：
  1. 默认：xluohome/phonedata 的 phone.dat（二进制：号段 + 省 + 市 + 邮编 + 区号 + 运营商）
  2. --csv path.csv：phone-segment 风格 CSV，列：prefix,province,city,isp
  3. --dat path.dat：本地已下载的 phone.dat（跳过下载）

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
    """解析 xluohome/phonedata 的 phone.dat。

    文件结构：
      - 头部 4 字节为版本号（uint32 小端），记录区从 offset=4 开始；
      - 每条记录：int32 号段(小端) + 5 个以 \\0 结尾的 GBK 字符串
        （province, city, zipCode, areaCode, isp）。
    """
    with open(path, "rb") as f:
        data = f.read()
    if len(data) < 8:
        raise ValueError("phone.dat 文件过小或非预期格式")
    records = []
    pos = 4
    n = len(data)
    while pos + 4 <= n:
        prefix = struct.unpack_from("<i", data, pos)[0]
        pos += 4
        parts = []
        for _ in range(5):  # province, city, zipCode, areaCode, isp
            start = pos
            while pos < n and data[pos] != 0:
                pos += 1
            raw = data[start:pos]
            pos += 1  # 跳过 NUL
            try:
                parts.append(raw.decode("gbk"))
            except Exception:
                parts.append(raw.decode("utf-8", "ignore"))
        province, city, _zip, _area, isp = parts
        records.append((prefix, province, city, isp))
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
