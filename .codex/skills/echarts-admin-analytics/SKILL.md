---
name: echarts-admin-analytics
description: Use when creating or improving ECharts dashboards, token audit charts, task trends, cost visualizations, operational metrics, or analytics panels in the CodeInsight frontend.
---

# ECharts Admin Analytics

## Core Principle

Charts should answer operational questions quickly. Prefer readable labels, clear units, stable legends, and useful empty states over decorative complexity.

## Good Uses

- Dashboard task throughput and exception trends.
- Token audit cost, input/output token mix, and model usage.
- Knowledge coverage and pending review counts.
- Repository scan trends and task duration distributions.

## Chart Selection

- Line: trends over time.
- Bar: category comparison, task count, model usage.
- Stacked bar: input/output token split or status composition.
- Pie/donut: only for small status distributions.
- Heatmap/calendar: daily workload or review activity when dense data exists.

## Implementation Rules

- Use `echarts-for-react` already present in the project.
- Memoize options when charts depend on expensive transforms.
- Keep chart containers with stable height to prevent layout jumps.
- Use AntD `Empty`, `Skeleton`, or `Spin` around loading and empty states.
- Format units: tokens, ms/s/min, count, percent, cost.

## Avoid

- 3D charts, unnecessary animations, and unreadable gradients.
- Multiple legends competing in a compact admin panel.
- Rendering charts before container dimensions are stable.
- Encoding critical status only by color without labels.
