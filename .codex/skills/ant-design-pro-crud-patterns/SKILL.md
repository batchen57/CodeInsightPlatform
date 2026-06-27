---
name: ant-design-pro-crud-patterns
description: Use when designing or refactoring CRUD pages, searchable tables, forms, drawers, batch actions, ProTable, ProForm, or Ant Design ProComponents patterns in this admin frontend.
---

# Ant Design Pro CRUD Patterns

## Core Principle

CRUD pages should be fast to scan and safe to operate. Favor filterable tables, clear primary actions, inline status, and drawer/modal forms over bespoke layouts.

## When ProComponents Fits

Use ProComponents patterns for:

- Systems, repositories, models, prompts, logs, and token audit lists.
- Search forms tied to paginated tables.
- Create/edit forms with repeated validation, grouped fields, or drawer workflows.
- Detail pages that need `Descriptions`, timeline, or operation history.

Use plain AntD when:

- The page is highly custom, such as a review editor or task flow monitor.
- The dependency is not installed and the task does not include adding it.
- A small form/table is clearer without abstraction.

## Page Pattern

- Top area: compact filters and primary action.
- Main area: table with status tags, owner/time columns, and action buttons.
- Create/edit: modal or drawer with `Form`.
- Destructive actions: confirm with `Popconfirm` or modal.
- Long-running actions: show progress/status and disable repeated clicks.

## Table Rules

- Keep row actions predictable: view, edit, enable/disable, test, delete.
- Put IDs and timestamps in narrower columns.
- Use ellipsis/tooltips for Git URLs, file paths, and long prompt names.
- Preserve pagination params between refreshes when users are managing lists.

## Form Rules

- Mirror backend DTO names where possible.
- Validate required fields at the form level before calling mutations.
- Do not expose secrets in plain text after save; mask API keys and repository tokens.
