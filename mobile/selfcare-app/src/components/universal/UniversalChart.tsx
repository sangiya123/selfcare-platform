/**
 * UniversalChart — The single chart primitive. Handles line / bar / area /
 * pie / donut charts from a numeric series. Replaces ALL feature chart widgets.
 *
 * Component ID: UniversalChart
 * All styling via ThemeEngine tokens. Zero hardcoded values.
 *
 * Manifest config:
 * {
 *   primitive: "UniversalChart",
 *   config: {
 *     type: "line" | "bar" | "area" | "pie" | "donut",
 *     // Data binding — resolve up to one series from the section `data`.
 *     //   - array data: pick label + value paths from each row
 *     //   - object data: optional `listPath` into the array, then row paths
 *     seriesPath?: "path.to.array",        // object-data only; default "items"
 *     labelPath: "period.label",           // x-axis / pie label per row
 *     valuePath: "data.remainingBytes",    // numeric value per row (default "value")
 *     // Rendering
 *     height?: number,                     // chart area height px (default 180)
 *     showLegend?: boolean,
 *     showValues?: boolean,                // value labels (line/bar)
 *     fill?: boolean,                      // area fill under line
 *     colors?: string[]                    // override series/pie colors (hex or theme colors:* key)
 *   }
 * }
 */

import React from 'react';
import { View, StyleSheet } from 'react-native';
import Svg, { Path, Circle, Line, Rect, G, Text as SvgText } from 'react-native-svg';
import type { PrimitiveProps } from '../ComponentRegistry';
import { useTheme, ResolvedTheme } from '../../manifest/ThemeEngine';
import { useLocalize } from '../../manifest/Localization';
import { UniversalText } from './UniversalText';

type ChartType = 'line' | 'bar' | 'area' | 'pie' | 'donut';

interface UniversalChartPropsConfig {
  type?: ChartType;
  seriesPath?: string;
  labelPath?: string;
  valuePath?: string;
  height?: number;
  showLegend?: boolean;
  showValues?: boolean;
  fill?: boolean;
  colors?: string[];
}

interface ChartRow {
  label: string;
  value: number;
}

function getAt(obj: unknown, path: string | undefined): unknown {
  if (!path) return undefined;
  let cur: unknown = obj;
  for (const key of path.split('.')) {
    if (cur === null || cur === undefined) return undefined;
    cur = (cur as Record<string, unknown>)[key];
  }
  return cur;
}

function toNumber(v: unknown): number | null {
  if (typeof v === 'number') return v;
  if (typeof v === 'string') {
    const n = parseFloat(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

/** Normalize `data` into ordered rows with a resolved array + label/value paths. */
function resolveRows(
  data: unknown,
  config: UniversalChartPropsConfig
): ChartRow[] {
  let arr: unknown[] | null = null;

  if (Array.isArray(data)) {
    arr = data;
  } else if (data && typeof data === 'object') {
    const listPath = config.seriesPath || 'items';
    const nested = getAt(data, listPath);
    if (Array.isArray(nested)) arr = nested;
    else if (Array.isArray((data as Record<string, unknown>).data)) {
      arr = (data as Record<string, unknown>).data as unknown[];
    }
  }

  if (!arr) return [];

  return arr
    .map((row) => {
      const rawValue = getAt(row, config.valuePath || 'value');
      const value = toNumber(rawValue);
      if (value === null) return null;
      const label = String(getAt(row, config.labelPath) ?? '');
      return { label, value };
    })
    .filter((r): r is ChartRow => r !== null);
}

function resolveColor(raw: string | undefined, theme: ResolvedTheme, fallbackIndex: number): string {
  if (!raw) {
    const palette = theme.colors.chart ?? theme.colors;
    const keys = ['primary500', 'secondary500', 'tertiary500', 'success'];
    return (
      (palette as unknown as Record<string, string>)[keys[fallbackIndex % keys.length]] ??
      theme.colors.primary500 ??
      '#4444cc'
    );
  }
  if (raw.startsWith('colors:')) {
    const key = raw.slice('colors:'.length);
    const colorMap = theme.colors as unknown as Record<string, string>;
    return colorMap[key] ?? theme.colors.primary500 ?? '#4444cc';
  }
  return raw;
}

export function UniversalChart(props: PrimitiveProps): React.JSX.Element {
  const config = (props.props || {}) as UniversalChartPropsConfig;
  const type: ChartType = config.type || 'line';
  const theme = useTheme();
  const { t } = useLocalize();
  const chartColors = config.colors ?? [];
  const height = config.height ?? 180;

  if (props.isLoading || (!props.data && !props.error)) {
    return <UniversalChartSkeleton height={height} theme={theme} config={config} />;
  }

  const rows = resolveRows(props.data, config);
  const empty = rows.length === 0;

  if (props.error) {
    return (
      <View style={universalChartStyles(theme).errorContainer}>
        <UniversalText data={null} props={{ variant: 'body', value: t('chart.loadError', { default: "Couldn't load chart" }), color: 'error' }} />
      </View>
    );
  }

  if (empty) {
    return (
      <View style={[universalChartStyles(theme).emptyContainer, { height }]}>
        <UniversalText data={null} props={{ variant: 'caption', value: t('chart.empty', { default: 'No data available' }), color: 'secondary' }} />
      </View>
    );
  }

  const legend = config.showLegend || type === 'pie' || type === 'donut';

  return (
    <View style={universalChartStyles(theme).container}>
      <Svg width="100%" height={height}>
        {type === 'pie' || type === 'donut' ? (
          <PieChart rows={rows} colors={chartColors} theme={theme} donut={type === 'donut'} showValues={config.showValues} />
        ) : (
          <CartesianChart rows={rows} config={config} colors={chartColors} height={height} theme={theme} />
        )}
      </Svg>
      {legend ? <Legend rows={rows} colors={chartColors} theme={theme} type={type} /> : null}
    </View>
  );
}

function CartesianChart({
  rows,
  config,
  colors,
  height,
  theme,
}: {
  rows: ChartRow[];
  config: UniversalChartPropsConfig;
  colors: string[];
  height: number;
  theme: ResolvedTheme;
}): React.JSX.Element {
  const type: ChartType = config.type || 'line';
  const c = theme.colors;
  const padL = 4;
  const padR = 4;
  const padT = 12;
  const padB = 20;
  const w = 320; // layout width — Svg coords scaled by viewBox
  const plotW = w - padL - padR;
  const plotH = height - padT - padB;

  const maxV = Math.max(...rows.map((r) => r.value), 1);
  const minV = Math.min(...rows.map((r) => r.value), 0);
  const span = maxV - minV || 1;

  const x = (i: number) => padL + (rows.length === 1 ? plotW / 2 : (i / (rows.length - 1)) * plotW);
  const y = (v: number) => padT + plotH - ((v - minV) / span) * plotH;

  const stroke = resolveColor(colors[0], theme, 0);

  const lineSegments = rows.map((r, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(r.value)}`).join(' ');
  const areaPath =
    `${lineSegments} L${x(rows.length - 1)},${padT + plotH} L${x(0)},${padT + plotH} Z`;

  const gridLines = 4;
  const grid = Array.from({ length: gridLines + 1 }, (_, i) => {
    const gv = minV + (span * i) / gridLines;
    const gy = y(gv);
    return { gy, gv };
  });

  const labelEvery = rows.length > 6 ? Math.ceil(rows.length / 6) : 1;

  return (
    <G>
      {grid.map((g, gi) => (
        <G key={gi}>
          <Line x1={padL} y1={g.gy} x2={w - padR} y2={g.gy} stroke={(c.outline ?? c.border) + '55'} strokeWidth={1} />
          <SvgText x={padL + 2} y={g.gy - 3} fontSize={9} fill={(c.textSecondary ?? c.onSurface) + 'aa'}>
            {shortNumber(g.gv)}
          </SvgText>
        </G>
      ))}

      {(type === 'line' || type === 'area') && (
        <>
          {config.fill || type === 'area' ? <Path d={areaPath} fill={stroke + '33'} stroke="none" /> : null}
          <Path d={lineSegments} fill="none" stroke={stroke} strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
        </>
      )}

      {type === 'bar' &&
        rows.map((r, i) => {
          const bw = Math.max(4, plotW / rows.length - 4);
          const bx = x(i) - bw / 2;
          const by = y(Math.max(r.value, 0));
          const bh = Math.max(1, padT + plotH - by);
          return <Rect key={i} x={bx} y={by} width={bw} height={bh} rx={2} fill={resolveColor(colors[i], theme, i)} />;
        })}

      {type === 'bar' ? null : rows.map((r, i) => <Circle key={i} cx={x(i)} cy={y(r.value)} r={3} fill={stroke} />)}

      {config.showValues &&
        rows.map((r, i) => (
          <SvgText key={`v${i}`} x={x(i)} y={y(r.value) - 6} fontSize={9} fill={c.textSecondary ?? '#888888'} textAnchor="middle">
            {shortNumber(r.value)}
          </SvgText>
        ))}

      {rows.map((r, i) =>
        i % labelEvery === 0 ? (
          <SvgText key={`l${i}`} x={x(i)} y={height - 5} fontSize={9} fill={(c.textSecondary ?? c.onSurface) + 'aa'} textAnchor="middle">
            {shortLabel(r.label)}
          </SvgText>
        ) : null
      )}
    </G>
  );
}

function PieChart({
  rows,
  colors,
  theme,
  donut,
  showValues,
}: {
  rows: ChartRow[];
  colors: string[];
  theme: ResolvedTheme;
  donut: boolean;
  showValues?: boolean;
}): React.JSX.Element {
  const total = rows.reduce((acc, r) => acc + r.value, 0) || 1;
  const cx = 100;
  const cy = 90;
  const r = 70;
  const innerR = donut ? r * 0.55 : 0;

  const c = theme.colors;
  const arcs: Array<{ d: string; color: string; pct: number; midAngle: number }> = [];
  let acc = 0;
  rows.forEach((row, i) => {
    const a0 = (acc / total) * 2 * Math.PI - Math.PI / 2;
    acc += row.value;
    const a1 = (acc / total) * 2 * Math.PI - Math.PI / 2;
    const large = a1 - a0 > Math.PI ? 1 : 0;

    if (donut) {
      // Annular sector path (donut)
      const x0 = cx + r * Math.cos(a0);
      const y0 = cy + r * Math.sin(a0);
      const x1 = cx + r * Math.cos(a1);
      const y1 = cy + r * Math.sin(a1);
      const ix0 = cx + innerR * Math.cos(a1);
      const iy0 = cy + innerR * Math.sin(a1);
      const ix1 = cx + innerR * Math.cos(a0);
      const iy1 = cy + innerR * Math.sin(a0);
      arcs.push({
        d: `M${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1} L${ix0},${iy0} A${innerR},${innerR} 0 ${large} 0 ${ix1},${iy1} Z`,
        color: resolveColor(colors[i], theme, i),
        pct: (row.value / total) * 100,
        midAngle: (a0 + a1) / 2,
      });
    } else {
      const x0 = cx + r * Math.cos(a0);
      const y0 = cy + r * Math.sin(a0);
      const x1 = cx + r * Math.cos(a1);
      const y1 = cy + r * Math.sin(a1);
      arcs.push({
        d: `M${cx},${cy} L${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1} Z`,
        color: resolveColor(colors[i], theme, i),
        pct: (row.value / total) * 100,
        midAngle: (a0 + a1) / 2,
      });
    }
  });

  return (
    <G>
      {arcs.map((a, i) => (
        <Path key={i} d={a.d} fill={a.color} />
      ))}
      {showValues &&
        arcs.map((a, i) => {
          const lx = cx + (r - 18) * Math.cos(a.midAngle);
          const ly = cy + (r - 18) * Math.sin(a.midAngle);
          return (
            <SvgText key={`p${i}`} x={lx} y={ly} fontSize={9} fill="#ffffff" textAnchor="middle" fontWeight="600">
              {Math.round(a.pct)}%
            </SvgText>
          );
        })}
    </G>
  );
}

function Legend({
  rows,
  colors,
  theme,
  type,
}: {
  rows: ChartRow[];
  colors: string[];
  theme: ResolvedTheme;
  type: ChartType;
}): React.JSX.Element {
  const s = universalChartStyles(theme);
  return (
    <View style={s.legend}>
      {rows.map((r, i) => (
        <View key={i} style={s.legendItem}>
          <View style={[s.legendDot, { backgroundColor: resolveColor(colors[i], theme, i) }]} />
          <UniversalText data={null} props={{ variant: 'caption', value: r.label || String(i + 1), color: 'secondary' }} />
        </View>
      ))}
    </View>
  );
}

function shortNumber(v: number): string {
  if (Math.abs(v) >= 1_000_000) return `${(v / 1_000_000).toFixed(1)}M`;
  if (Math.abs(v) >= 1000) return `${(v / 1000).toFixed(v >= 10000 ? 0 : 1)}k`;
  return `${Math.round(v)}`;
}

function shortLabel(label: string): string {
  if (label.length <= 10) return label;
  return `${label.slice(0, 9)}…`;
}

function UniversalChartSkeleton({ height, theme, config }: { height: number; theme: ResolvedTheme; config: UniversalChartPropsConfig }) {
  const s = universalChartStyles(theme);
  const type = config.type || 'line';
  return (
    <View style={[s.skeleton, { height }]}>
      {type === 'pie' || type === 'donut' ? (
        <View style={[s.skeletonPie, { backgroundColor: theme.colors.onSurface + '12' }]} />
      ) : (
        <View style={[s.skeletonBars, { backgroundColor: theme.colors.onSurface + '12' }]} />
      )}
    </View>
  );
}

function universalChartStyles(theme: ResolvedTheme) {
  const c = theme.colors;
  const spacing = theme.spacing;
  const radii = theme.borderRadius;
  return StyleSheet.create({
    container: {
      paddingHorizontal: spacing[1],
      paddingVertical: spacing[1],
      borderRadius: radii.md,
      backgroundColor: c.surfaceContainerLow ?? c.surface,
    },
    legend: {
      flexDirection: 'row',
      flexWrap: 'wrap',
      gap: spacing[2],
      marginTop: spacing[1],
      paddingHorizontal: spacing[1],
    },
    legendItem: { flexDirection: 'row', alignItems: 'center', gap: 4 },
    legendDot: { width: 10, height: 10, borderRadius: 5 },
    errorContainer: { padding: spacing[3], alignItems: 'center' },
    emptyContainer: { alignItems: 'center', justifyContent: 'center' },
    skeleton: {
      borderRadius: radii.md,
      backgroundColor: c.surfaceContainerLow ?? c.surface,
      alignItems: 'center',
      justifyContent: 'center',
      overflow: 'hidden',
    },
    skeletonPie: { width: 90, height: 90, borderRadius: 45 },
    skeletonBars: {
      width: '80%',
      height: 40,
      borderRadius: radii.sm,
    },
  });
}