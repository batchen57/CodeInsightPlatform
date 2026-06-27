---
name: zustand-client-state-boundaries
description: Use when adding Zustand stores, shared frontend state, draft editor state, filters, layout preferences, or deciding whether data belongs in client state versus server cache.
---

# Zustand Client State Boundaries

## Core Principle

Use Zustand for client-owned state, not as a shadow database for backend data. Server entities should remain in API wrappers or TanStack Query cache.

## Good Zustand Uses

- Collapsed panels, selected tabs, density preferences, and local UI mode.
- Draft editor buffer, unsaved markers, source selection, and review workspace focus.
- Cross-page filter presets when users expect the choice to persist.
- Temporary optimistic UI state that does not duplicate a whole backend table.

## Poor Zustand Uses

- Storing task lists, repositories, logs, or model records fetched from backend APIs.
- Keeping multiple unsynchronized copies of the same server entity.
- Globalizing state only because prop drilling appears once.
- Mixing actions that call APIs directly into large stores without a clear boundary.

## Store Shape

Prefer small domain stores:

```ts
type DraftWorkspaceState = {
  activeDraftId?: number;
  editorMode: 'edit' | 'preview' | 'diff';
  setActiveDraftId: (id?: number) => void;
  setEditorMode: (mode: DraftWorkspaceState['editorMode']) => void;
};
```

## Rules

- Keep selectors narrow to avoid broad re-renders.
- Store IDs and UI choices instead of full server objects.
- Reset transient state when leaving a workflow if stale state would confuse review or task execution.
- Persist only low-risk preferences; do not persist secrets, tokens, API keys, or draft content unless the product requires it.
