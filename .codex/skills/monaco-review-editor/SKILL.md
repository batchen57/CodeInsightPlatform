---
name: monaco-review-editor
description: Use when building or improving Monaco Editor experiences for markdown drafts, code references, diff views, source line annotations, review comments, or AI-generated knowledge editing.
---

# Monaco Review Editor

## Core Principle

Use Monaco as a review workspace, not just a text box. Preserve source traceability, unsaved state, and reviewer confidence.

## Fit For This Project

Monaco is best used in:

- Knowledge draft review and Markdown editing.
- Source code reference panes with line numbers.
- AI output versus reviewer revision comparison.
- Diff views for generated docs before confirmation or push.

## Editor Patterns

- Use controlled state only when needed; avoid re-rendering the editor on every parent update.
- Keep editor height stable with responsive constraints.
- Use readonly Monaco instances for source references.
- Use language modes intentionally: `markdown`, `java`, `json`, `yaml`, or `diff`.
- Add decorations for source lines, unresolved comments, warnings, and pending confirmation markers.

## Review Workflow

- Show save/autosave status close to the editor.
- Preserve cursor/scroll position when refreshing metadata.
- Pair editor content with source references and revision history.
- Confirm destructive edits or version replacement.

## Avoid

- Storing large editor content globally unless the workflow needs it.
- Reinitializing Monaco when switching tabs.
- Hiding validation errors inside console logs.
- Using Monaco for simple short text fields where AntD `Input.TextArea` is enough.
