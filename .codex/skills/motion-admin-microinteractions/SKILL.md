---
name: motion-admin-microinteractions
description: Use when adding motion, transitions, loading choreography, task state animations, route transitions, drawer/modal motion, or React Motion patterns in this enterprise admin frontend.
---

# Motion Admin Microinteractions

## Core Principle

Motion should clarify state change, not decorate the product. Keep animations brief, restrained, and useful for an operations console.

## Good Uses

- Task status progression and progress updates.
- Route or detail-panel transitions that preserve orientation.
- Drawer/modal entrance and content reveal.
- Loading-to-loaded transitions for analytics cards.
- Highlighting newly created or updated rows.

## Guidelines

- Prefer AntD built-in motion before adding custom animation.
- Keep duration around 120-240ms for routine UI.
- Use easing that feels calm, not playful.
- Respect reduced-motion preferences.
- Animate opacity/transform rather than layout-heavy properties.

## Avoid

- Large page choreography that slows repeated work.
- Decorative background motion.
- Animations that move table rows while users are reading.
- Motion that hides loading, error, or disabled states.

## Verification

Check desktop and mobile widths after adding motion. Ensure text does not overlap, buttons remain clickable, and reduced-motion users still get clear state feedback.
