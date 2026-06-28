---
name: react-router-layout-routes
description: Use when adding or changing React Router routes, nested routes, detail pages, navigation menus, redirects, breadcrumbs, or layout behavior in the CodeInsight frontend.
---

# React Router Layout Routes

## Core Principle

Keep routing simple and explicit: `createHashRouter` owns route definitions, `BasicLayout` owns the app shell, and child routes render through `Outlet`.

## Existing Pattern

- Router file: `frontend/src/router/index.tsx`.
- Layout file: `frontend/src/layouts/BasicLayout.tsx`.
- Navigation source: `navigation` array in `BasicLayout.tsx`.
- Detail pattern: list route such as `tasks`, detail route such as `tasks/:id`.

## Adding A Page

1. Create `frontend/src/pages/<domain>/index.tsx` or `<name>.tsx`.
2. Import it in `router/index.tsx`.
3. Add it as a child route under `/`.
4. Add a matching navigation item only when the page is a top-level workflow.
5. Update `getSelectedKey` behavior only if the new path has a non-obvious parent.

## Route Design

- Keep Hash Router unless explicitly asked to change deployment strategy.
- Use nested routes for pages that share the shell.
- Use URL params for stable entity details: `tasks/:id`, `systems/:id`.
- Keep modal-only state out of the URL unless users need shareable/deep-linked state.
- Prefer `Link` for navigation and `useNavigate` for command completion flows.

## Common Mistakes

- Adding a top-level route without a navigation title/description.
- Breaking menu highlight for detail pages.
- Creating a second layout instead of extending `BasicLayout`.
- Moving route configuration into page files without a clear reason.
