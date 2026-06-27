---
name: axios-query-server-state
description: Use when adding frontend data fetching, pagination, cache invalidation, mutations, polling, API error handling, or TanStack Query patterns around the existing Axios request layer.
---

# Axios Query Server State

## Core Principle

Separate server state from UI state. Axios owns HTTP transport and response unwrapping; TanStack Query should own cache, loading, refetch, mutation, polling, and invalidation when introduced.

## Current Baseline

- HTTP client: `frontend/src/api/request.ts`.
- Domain wrappers: `frontend/src/api/*.ts`.
- Backend success shape: `{ code: 0, message: "success", data }`.
- The Axios interceptor returns `data`, so callers should not read `response.data.data`.

## API Wrapper Pattern

- Keep raw endpoint calls in `api/<domain>.ts`.
- Use typed params and typed return values.
- Do not call Axios directly from pages unless creating a new domain wrapper first.
- Preserve backend path names: `/systems`, `/repositories`, `/tasks`, `/drafts`, `/knowledge`, `/token-audit`, `/logs`.

## TanStack Query Boundary

When TanStack Query is available:

- Use queries for lists, details, stats, and read-only resources.
- Use mutations for create, update, delete, start, retry, confirm, push, and export actions.
- Invalidate precise domain keys after successful mutations.
- Use polling only for task progress or live audit panels, and stop polling for terminal statuses.

Suggested key shape:

```ts
['tasks', 'list', params]
['tasks', 'detail', id]
['tasks', 'progress', id]
```

## Avoid

- Duplicating server lists into Zustand.
- Hand-rolled loading/error state for data that Query can own.
- Swallowing errors that the global interceptor already reports.
- Creating broad invalidations such as every query after every mutation.
