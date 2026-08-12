/** 通用 SVG 折线图（零依赖，M7.10 登录趋势曲线）：
 *  网格线 + 折线 + 面积填充 + 数据点数值 + x 轴标签。
 *  数据全为 0 时显示空态文字。 */
interface TrendChartProps {
  points: { label: string; count: number }[];
  height?: number;
  color?: string;
}

export default function TrendChart({
  points,
  height = 210,
  color = "#1677ff",
}: TrendChartProps) {
  const W = 720;
  const H = 250;
  const padL = 34;
  const padR = 14;
  const padT = 26;
  const padB = 32;
  const plotW = W - padL - padR;
  const plotH = H - padT - padB;

  const n = points.length;
  const max = Math.max(1, ...points.map((p) => p.count));
  const allZero = points.every((p) => p.count === 0);

  const xAt = (i: number) =>
    n <= 1 ? padL + plotW / 2 : padL + (i * plotW) / (n - 1);
  const yAt = (c: number) => padT + plotH - (c / max) * plotH;

  const linePath = points
    .map(
      (p, i) =>
        `${i === 0 ? "M" : "L"}${xAt(i).toFixed(1)},${yAt(p.count).toFixed(1)}`,
    )
    .join(" ");
  const areaPath = `${linePath} L${xAt(n - 1).toFixed(1)},${(padT + plotH).toFixed(1)} L${xAt(0).toFixed(1)},${(padT + plotH).toFixed(1)} Z`;

  // 5 条水平网格线（含 0 与最大值）
  const gridLines = [0, 1, 2, 3, 4].map((i) => {
    const v = (max * (4 - i)) / 4;
    return { y: yAt(v), v: Math.round(v) };
  });

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      style={{ width: "100%", height }}
      role="img"
      aria-label="登录次数趋势图"
    >
      {/* 水平网格线 + y 轴刻度 */}
      {gridLines.map((g, i) => (
        <g key={i}>
          <line
            x1={padL}
            y1={g.y}
            x2={W - padR}
            y2={g.y}
            stroke="#eef0f4"
            strokeWidth={1}
          />
          <text
            x={padL - 6}
            y={g.y + 3}
            textAnchor="end"
            fontSize={10}
            fill="#999"
          >
            {g.v}
          </text>
        </g>
      ))}

      {/* 面积填充 + 折线 */}
      <path d={areaPath} fill={color} opacity={0.08} stroke="none" />
      {!allZero && (
        <path
          d={linePath}
          fill="none"
          stroke={color}
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
      )}

      {/* 数据点 + 数值标注 */}
      {points.map((p, i) => {
        const x = xAt(i);
        const y = yAt(p.count);
        return (
          <g key={i}>
            {p.count > 0 && <circle cx={x} cy={y} r={3.2} fill={color} />}
            <text x={x} y={y - 8} textAnchor="middle" fontSize={10} fill="#666">
              {p.count}
            </text>
          </g>
        );
      })}

      {/* x 轴标签 */}
      {points.map((p, i) => (
        <text
          key={`l${i}`}
          x={xAt(i)}
          y={H - 10}
          textAnchor="middle"
          fontSize={10}
          fill="#999"
        >
          {p.label}
        </text>
      ))}

      {/* 空态 */}
      {allZero && (
        <text
          x={padL + plotW / 2}
          y={padT + plotH / 2}
          textAnchor="middle"
          fontSize={13}
          fill="#bbb"
        >
          暂无登录记录
        </text>
      )}
    </svg>
  );
}
