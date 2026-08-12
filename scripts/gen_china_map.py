# -*- coding: utf-8 -*-
"""
M7.13：生成中国地图 SVG 数据 + 城市质心坐标表 → frontend/src/mapdata/chinaMapData.ts

数据源：阿里 DataV GeoJSON
  - 省级轮廓：https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json（已下载到 scripts/china_100000_full.json）
  - 各市质心：逐省下载 {adcode}_full.json，取 properties.center（行政中心坐标）

投影：等距圆柱 + 中纬度余弦修正（cos35°），保证宽高比接近真实地图。
用法：python scripts/gen_china_map.py
"""
import json
import math
import os
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PROVINCE_FILE = os.path.join(HERE, "china_100000_full.json")
OUT_FILE = os.path.join(ROOT, "frontend", "src", "mapdata", "chinaMapData.ts")
CACHE_DIR = os.path.join(HERE, "geo_cache")

# 中国边界（略留边）
MIN_LON, MAX_LON = 72.0, 136.0
MIN_LAT, MAX_LAT = 15.0, 56.0
COS_LAT = math.cos(math.radians(35.0))  # 中纬度修正
SCALE = 14.0  # 每经度(修正后)像素数
PAD = 10  # viewBox 内边距


def project(lon: float, lat: float):
    x = (lon - MIN_LON) * COS_LAT * SCALE + PAD
    y = (MAX_LAT - lat) * SCALE + PAD
    return x, y


VIEW_W = int((MAX_LON - MIN_LON) * COS_LAT * SCALE + PAD * 2)
VIEW_H = int((MAX_LAT - MIN_LAT) * SCALE + PAD * 2)


def ring_to_path(ring):
    """坐标环 → SVG path 片段（2 位小数）"""
    parts = []
    for i, (lon, lat) in enumerate(ring):
        x, y = project(lon, lat)
        parts.append(("M" if i == 0 else "L") + f"{x:.1f} {y:.1f}")
    parts.append("Z")
    return "".join(parts)


def geometry_to_path(geom):
    d = []
    if geom["type"] == "Polygon":
        for ring in geom["coordinates"]:
            d.append(ring_to_path(ring))
    elif geom["type"] == "MultiPolygon":
        for poly in geom["coordinates"]:
            for ring in poly:
                d.append(ring_to_path(ring))
    return "".join(d)


def fetch_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def simplify_name(name: str) -> str:
    """去掉行政后缀，兼容 ip2region 返回的省/市名（如 广东省→广东、深圳市→深圳）"""
    for suffix in ("壮族自治区", "回族自治区", "维吾尔自治区", "自治区", "特别行政区", "省", "市"):
        if name.endswith(suffix):
            return name[: -len(suffix)]
    return name


def main():
    os.makedirs(CACHE_DIR, exist_ok=True)
    with open(PROVINCE_FILE, encoding="utf-8") as f:
        provinces = json.load(f)["features"]

    map_paths = []
    province_coords = {}
    city_coords = {}

    for feat in provinces:
        props = feat["properties"]
        name = props.get("name")
        adcode = props.get("adcode")
        map_paths.append(geometry_to_path(feat["geometry"]))
        # 九段线等要素无 center/centroid/adcode，只画轮廓不记坐标
        center = props.get("center") or props.get("centroid")
        if name and adcode and center:
            cx, cy = project(*center)
            province_coords[simplify_name(name)] = [round(cx, 1), round(cy, 1)]
        else:
            print(f"[INFO] 跳过无坐标要素：{name or adcode}")
            continue

        # 逐省下载市级 GeoJSON（带本地缓存）
        cache = os.path.join(CACHE_DIR, f"{adcode}.json")
        if os.path.exists(cache):
            with open(cache, encoding="utf-8") as f:
                sub = json.load(f)
        else:
            url = f"https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json"
            try:
                sub = fetch_json(url)
                with open(cache, "w", encoding="utf-8") as f:
                    json.dump(sub, f, ensure_ascii=False)
                time.sleep(0.15)  # 轻限流
            except Exception as e:
                print(f"[WARN] 下载 {name}({adcode}) 失败：{e}")
                continue

        for c in sub["features"]:
            cp = c["properties"]
            if cp.get("level") not in ("city", "district"):
                continue  # 直辖市下是 district，也收进来
            ccx, ccy = project(*cp["center"])
            city_coords[simplify_name(cp["name"])] = [round(ccx, 1), round(ccy, 1)]

    print(f"省 {len(province_coords)} 个，市/区 {len(city_coords)} 个，路径 {len(map_paths)} 段")

    # 生成 TS
    lines = [
        "// 自动生成（scripts/gen_china_map.py），勿手改。M7.13 访问者地理分布图。",
        f'export const MAP_VIEW_BOX = "0 0 {VIEW_W} {VIEW_H}";',
        "",
        "/** 省级行政区轮廓 SVG path（等距投影，cos35° 修正） */",
        "export const MAP_PATHS: string[] = [",
    ]
    for p in map_paths:
        lines.append("  " + json.dumps(p, ensure_ascii=False) + ",")
    lines.append("];")
    lines.append("")
    lines.append("/** 省名（去后缀）→ SVG 坐标 [x, y] */")
    lines.append("export const PROVINCE_COORDS: Record<string, [number, number]> = {")
    for k, v in province_coords.items():
        lines.append(f"  {json.dumps(k, ensure_ascii=False)}: [{v[0]}, {v[1]}],")
    lines.append("};")
    lines.append("")
    lines.append("/** 市/区名（去后缀）→ SVG 坐标 [x, y] */")
    lines.append("export const CITY_COORDS: Record<string, [number, number]> = {")
    for k, v in city_coords.items():
        lines.append(f"  {json.dumps(k, ensure_ascii=False)}: [{v[0]}, {v[1]}],")
    lines.append("};")
    lines.append("")

    os.makedirs(os.path.dirname(OUT_FILE), exist_ok=True)
    with open(OUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    size_kb = os.path.getsize(OUT_FILE) / 1024
    print(f"已生成 {OUT_FILE}（{size_kb:.0f} KB）")


if __name__ == "__main__":
    main()
