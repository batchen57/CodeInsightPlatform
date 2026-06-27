---
name: antd-token-admin-ui
description: Use when styling or building Ant Design admin UI in this frontend, especially when changing themes, colors, spacing, typography, tables, forms, cards, modals, or status presentation.
---

# Ant Design Token Admin UI

## Core Principle

Use Ant Design tokens and component semantics as the source of visual truth. Avoid page-local hardcoded colors and ad hoc spacing when the existing theme can carry the decision.

## Existing Theme

The project defines its AntD theme in `frontend/src/App.tsx` via `ConfigProvider`.

- Respect `colorPrimary`, semantic colors, text colors, layout background, borders, radius, font, and control height.
- Add component token overrides in `App.tsx` only when the change is truly global.
- For one-off layout CSS, prefer CSS variables or classes that harmonize with the existing `ci-*` shell styles.

## UI Patterns

- Use `Tag`/`Badge` for statuses, not custom colored spans.
- Use `Table`, `Form`, `Modal`, `Drawer`, `Tabs`, `Segmented`, `Steps`, `Descriptions`, `Statistic`, and `Alert` before custom widgets.
- Use Ant Design icons in buttons and toolbars when a standard icon exists.
- Keep button text short. Use icon-only buttons with tooltips for repeated table actions.
- Keep page headings compact inside the existing `BasicLayout` page heading.

## Avoid

- Hardcoded purple/blue gradients as the dominant design language.
- Oversized marketing typography in admin panels.
- Nested cards and decorative floating containers.
- Negative letter spacing or viewport-scaled font sizes.
- CSS that fights AntD component dimensions and causes table/form layout shift.

## Review Checklist

- Does the UI still read as a work console?
- Are statuses visually consistent across pages?
- Are colors and radii traceable to AntD tokens or the shell CSS?
- Does text fit inside buttons, table cells, cards, and mobile drawers?
