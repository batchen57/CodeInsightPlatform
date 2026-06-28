---
name: codeinsight-frontend-conventions
description: Use when editing the CodeInsight frontend, adding React pages, changing admin UI workflows, wiring frontend APIs, or reviewing frontend implementation in this repository.
---

# CodeInsight Frontend Conventions

## Core Principle

Build this frontend as a dense, professional code-knowledge operations console. Preserve the existing React 19 + Vite + Ant Design 6 + Hash Router structure unless the task explicitly asks for an architectural change.

## Project Shape

- Work under `frontend/src`.
- Keep domain pages in `pages/<domain>/`.
- Keep backend-aligned API wrappers in `api/<domain>.ts`.
- Keep route registration in `router/index.tsx`.
- Keep global shell/navigation behavior in `layouts/BasicLayout.tsx`.
- Keep shared DTOs in `types/index.ts` unless a domain file already owns a narrower type.

## UI Direction

- Favor quiet enterprise UI: scan-friendly tables, compact filters, clear status tags, and predictable actions.
- Do not create landing pages, marketing hero sections, decorative blobs, or card-heavy promotional layouts.
- Use Ant Design components first. Reach for custom CSS only for shell-level layout or when AntD composition cannot express the interaction.
- Keep cards for repeated items, modals, and genuinely framed tools. Do not put page sections inside nested cards.

## Integration Rules

- API responses are already unwrapped by `api/request.ts`; page code should consume the `data` payload directly.
- Use existing route keys and menu patterns when adding navigation.
- Keep Chinese UI copy consistent with the domain language: 系统, 仓库, 提示词, 任务, 知识复核, 推送, Token 审计, 操作日志.
- Prefer feature-local state for one-page interactions; introduce shared state only when multiple routes or components need it.

## Verification

After frontend edits, run from `frontend/`:

```bash
.\node_modules\.bin\eslint.cmd .
.\node_modules\.bin\tsc.cmd -b
```

Run `.\node_modules\.bin\vite.cmd build` when changes affect routing, bundling, imports, or production rendering.
