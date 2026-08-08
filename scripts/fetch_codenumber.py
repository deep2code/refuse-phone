#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
工信部电信网码号资源刷新脚本（可选工具）

用途：
    把「电信网码号资源使用和调整审批系统」(nac.miit.gov.cn) 公开的
    95/96/106/400/800 号段分配，刷新到 App 的 assets/seed_codenumber.csv，
    用于「陌生 95 号识别」。

为什么需要它：
    App 内置的 seed_codenumber.csv 只是一份「精选种子 + 号段类别兜底」，
    并不含全部已分配号段。想让识别更全，就用本脚本拉取最新分配。

两种用法：
    1) 浏览器手动导出（推荐，最稳）：
       - 打开 https://nac.miit.gov.cn/#/notice/gxb  ，点「号码查询」
       - 逐个号段查询，复制「使用单位 / 用途 / 有效期」到 manual_export.csv
         （格式：号段,类型,使用单位,用途,有效期,备注）
       - 运行：python fetch_codenumber.py convert manual_export.csv
       → 生成 app/src/main/assets/seed_codenumber.csv（含原有兜底行）

    2) 直接抓取（best-effort，可能需要浏览器/验证码）：
       - 该站点为动态页面，纯 requests 可能取不到数据；
         若你能抓到它的 JSON 接口，把 URL 填到下方 SITE_API 再运行：
         python fetch_codenumber.py fetch
       - 多数情况下该方式会失败并给出提示，请改用方式 1。

注意：
    - 数据仅用于个人防骗识别，请勿批量倒卖。
    - 生成的 CSV 首行是表头（prefix,type,owner,...），App 解析时会自动跳过。
"""

import argparse
import csv
import os
import sys

# 若你抓到了站点的真实 JSON 接口，把地址填到这里（否则 fetch 会直接提示用手动方式）。
SITE_API = os.environ.get("CODE_NUMBER_API", "")

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(HERE)
SEED_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "seed_codenumber.csv")

# 兜底类别行（保证即使种子为空，未知 95/96/400/800 也有合理提示）
FALLBACK_ROWS = [
    ["95", "95", "全国呼叫中心/银行客服号段", "银行/保险/企业客服", "",
     "具体单位需按完整号段在 nac.miit.gov.cn 查询；陌生 95 号多为合规客服，也常被冒用，请核验"],
    ["960", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "96 号段多为区域呼叫中心，陌生来电常为营销或催收，请警惕"],
    ["961", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["962", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["963", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["965", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["966", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["967", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["968", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["969", "96", "区域呼叫中心号段", "本地营销/催收/客服", "", "同上"],
    ["10690", "106", "三网合一短信通道", "短信验证码/通知", "", "增值电信"],
    ["10655", "106", "中国联通短信通道", "短信", "", "运营商"],
    ["10657", "106", "中国移动短信通道", "短信", "", "运营商"],
    ["10659", "106", "中国电信短信通道", "短信", "", "运营商"],
    ["1066", "106", "省级短信通道", "短信/营销", "", "增值电信"],
    ["1069", "106", "跨省/全国短信通道", "短信/营销", "", "增值电信"],
    ["400", "400", "企业全国统一客服号", "企业申请的客服热线", "", "具体单位需按完整号段在 nac.miit.gov.cn 查询"],
    ["800", "800", "企业免费客服号", "企业申请的免费客服热线", "", "具体单位需按完整号段在 nac.miit.gov.cn 查询"],
]


def _norm_type(prefix: str) -> str:
    if prefix.startswith("106"):
        return "106"
    if prefix.startswith("95"):
        return "95"
    if prefix.startswith("96"):
        return "96"
    if prefix.startswith("400"):
        return "400"
    if prefix.startswith("800"):
        return "800"
    return "OTHER"


def read_existing_seed() -> list:
    """读取现有 seed（保留手写的具体号段，追加新的）。"""
    rows = []
    if os.path.exists(SEED_PATH):
        with open(SEED_PATH, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("prefix"):
                    continue
                parts = line.split(",", limit=6)
                if len(parts) >= 3 and parts[0] and parts[2]:
                    rows.append([p.strip() for p in parts] + [""] * (6 - len(parts)))
    return rows


def write_seed(rows: list):
    os.makedirs(os.path.dirname(SEED_PATH), exist_ok=True)
    with open(SEED_PATH, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["prefix", "type", "owner", "purpose", "valid_until", "note"])
        for r in rows:
            w.writerow(r)
    print(f"已写入 {len(rows)} 行 -> {SEED_PATH}")


def cmd_convert(input_path: str):
    """把手动导出的 CSV（号段,类型,使用单位,用途,有效期,备注）转成 seed 格式。"""
    if not os.path.exists(input_path):
        print(f"找不到输入文件: {input_path}", file=sys.stderr)
        sys.exit(1)

    existing = {r[0] for r in read_existing_seed() if r and not r[0].startswith(("95", "96", "106", "400", "800"))}
    result = []
    with open(input_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        for i, parts in enumerate(reader):
            if i == 0 and parts and parts[0].startswith("号段"):
                continue
            if len(parts) < 3:
                continue
            prefix = parts[0].strip()
            owner = parts[2].strip()
            if not prefix or not owner:
                continue
            typ = parts[1].strip() or _norm_type(prefix)
            purpose = parts[3].strip() if len(parts) > 3 else ""
            valid = parts[4].strip() if len(parts) > 4 else ""
            note = parts[5].strip() if len(parts) > 5 else ""
            result.append([prefix, typ, owner, purpose, valid, note])

    # 合并：覆盖同名前缀，保留其他现有具体号段 + 兜底行
    merged = {r[0]: r for r in result}
    for r in read_existing_seed():
        if r[0] not in merged:
            merged[r[0]] = r
    for r in FALLBACK_ROWS:
        if r[0] not in merged:
            merged[r[0]] = r
    write_seed(list(merged.values()))


def cmd_fetch():
    """best-effort 直接抓取（多数情况需浏览器/验证码，失败请改 convert）。"""
    if not SITE_API:
        print("未配置 CODE_NUMBER_API，且站点纯 requests 通常取不到数据。")
        print("建议用法：")
        print("  1) 浏览器打开 https://nac.miit.gov.cn/#/notice/gxb 手动导出号段到 manual_export.csv")
        print("  2) 运行：python fetch_codenumber.py convert manual_export.csv")
        sys.exit(0)
    try:
        import requests
    except ImportError:
        print("需要 requests：pip install requests", file=sys.stderr)
        sys.exit(1)
    print(f"尝试抓取：{SITE_API}")
    print("（注：该站点为动态页面，纯 requests 可能返回空或验证码页；失败请改用 convert 方式）")
    # 仅占位：真实字段需按站点返回结构解析后调用 cmd_convert 的逻辑写入。
    # 此处不臆造解析逻辑，避免产生错误数据。
    print("请改用 convert 方式手动导出，保证数据准确。")


def main():
    parser = argparse.ArgumentParser(description="刷新工信部码号资源 seed_codenumber.csv")
    sub = parser.add_subparsers(dest="cmd")
    sub.add_parser("convert", help="把手动导出的 CSV 转成 seed").add_argument("input")
    sub.add_parser("fetch", help="best-effort 直接抓取（通常不可用）")
    args = parser.parse_args()
    if args.cmd == "convert":
        cmd_convert(args.input)
    elif args.cmd == "fetch":
        cmd_fetch()
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
