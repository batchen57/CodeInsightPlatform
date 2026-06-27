---
name: antd-x-ai-workflows
description: Use when adding AI-facing UI such as prompt test runs, model responses, streaming analysis, assistant panels, conversation-like review aids, Ant Design X components, or AI workflow feedback.
---

# Ant Design X AI Workflows

## Core Principle

AI UI in this project should support auditability and human review. Make model output traceable, interruptible, and clearly marked as draft or simulated content.

## When To Use

- Prompt test-run result panels.
- AI analysis progress and streaming output.
- Review assistant side panels.
- Model comparison or response inspection.
- Conversational helper flows around code knowledge.

## Design Rules

- Make AI-generated content visually distinct from confirmed knowledge.
- Surface model name, prompt version, token estimate, and failure state when relevant.
- Provide copy, retry, stop, and inspect-source actions where useful.
- Use Ant Design X components only when the interaction is genuinely AI/conversation oriented.
- Keep normal CRUD pages in regular AntD or ProComponents.

## States To Handle

- Mock AI versus real model.
- Streaming, completed, failed, cancelled.
- Empty prompt variables or missing model configuration.
- Token quota warning and quota blocking.

## Avoid

- Presenting AI output as final knowledge without review language.
- Hiding prompt/model metadata.
- Building chat UI for workflows that are better represented as forms, tables, or task timelines.
