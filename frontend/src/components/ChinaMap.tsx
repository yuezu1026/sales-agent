import { useState } from "react";
import {
  MAP_VIEW_BOX,
  MAP_PATHS,
  CITY_COORDS,
  PROVINCE_COORDS,
} from "../mapdata/chinaMapData";

/** 地理分布点（与后端 /api/auth/login-geo 对齐） */
export interface GeoPoint {
  province: string | null;
  city: string | null;
  count: number;
}

/** 去掉行政后缀，与 chinaMapData.ts 坐标表键名对齐（江苏省→江苏、南京市→南京） */
function stripSuffix(name: string): string {
  for (const s of [
    "壮族自治区",
    "回族自治区",
    "维吾尔自治区",
    "自治区",
    "特别行政区",
    "省",
    "市",
  ]) {
    if (name.endsWith(s)) {
      return name.slice(0, -s.length);
    }
  }
  return name;
}

/** 解析 viewBox 宽高 */
const [, , VB_W, VB_H] = MAP_VIEW_BOX.split(" ").map(Number);

interface ChinaMapProps {
  points: GeoPoint[];
}

/**
 * M7.13：中国地图访问者地理分布（自托管 SVG，零运行时外部请求）。
 * 散点定位：优先市级坐标，缺失回退省级坐标；半径随登录次数平方根缩放。
 */
export default function ChinaMap({ points }: ChinaMapProps) {
  const [hover, setHover] = useState<{
    label: string;
    count: number;
    x: number;
    y: number;
  } | null>(null);

  // 坐标解析 + 过滤掉无法定位的点
  const plotted = points
    .map((p) => {
      const city = p.city ? stripSuffix(p.city) : null;
      const province = p.province ? stripSuffix(p.province) : null;
      const coord =
        (city && CITY_COORDS[city]) ||
        (province && PROVINCE_COORDS[province]) ||
        null;
      return coord ? { ...p, coord, key: `${province}-${city}` } : null;
    })
    .filter((p): p is NonNullable<typeof p> => p !== null);

  const maxCount = Math.max(1, ...plotted.map((p) => p.count));
  const radius = (count: number) => 2.5 + Math.sqrt(count / maxCount) * 5.5; // 2.5~8（viewBox 单位）

  return (
    <div className="china-map-wrap">
      <svg
        viewBox={MAP_VIEW_BOX}
        className="china-map"
        role="img"
        aria-label="访问者全国地理分布图"
      >
        {/* 省级轮廓底图 */}
        {MAP_PATHS.map((d, i) => (
          <path
            key={i}
            d={d}
            fill="#eef3fb"
            stroke="#c3d3ea"
            strokeWidth={0.7}
          />
        ))}
        {/* 散点（按次数从小到大画，大点在下避免遮挡小点） */}
        {[...plotted]
          .sort((a, b) => a.count - b.count)
          .map((p) => (
            <circle
              key={p.key}
              cx={p.coord[0]}
              cy={p.coord[1]}
              r={radius(p.count)}
              fill="rgba(22,119,255,0.55)"
              stroke="#1677ff"
              strokeWidth={0.8}
              className="china-map-dot"
              onMouseEnter={() =>
                setHover({
                  label:
                    [
                      p.province,
                      p.city && p.city !== p.province ? p.city : null,
                    ]
                      .filter(Boolean)
                      .join(" · ") || "未知地区",
                  count: p.count,
                  x: p.coord[0],
                  y: p.coord[1],
                })
              }
              onMouseLeave={() => setHover(null)}
            />
          ))}
      </svg>
      {/* 悬浮提示（按 viewBox 比例换算成容器百分比定位） */}
      {hover && (
        <div
          className="china-map-tooltip"
          style={{
            left: `${(hover.x / VB_W) * 100}%`,
            top: `${(hover.y / VB_H) * 100}%`,
          }}
        >
          <div className="china-map-tooltip-label">{hover.label}</div>
          <div>登录 {hover.count} 次</div>
        </div>
      )}
      {plotted.length === 0 && (
        <div className="china-map-empty">
          暂无带地理位置的登录记录（新版本登录将自动记录归属地）
        </div>
      )}
    </div>
  );
}
