# Pixzel — implementation specification and prompt


A document set for GPT 6.1 Sol to implement **Pixzel**, a Kotlin UI library. Arc Engine is the primary integration; Pixzel's core is independent of Arc and any graphics API.


Basis: static inspection of [AxieFeat/Arc, dev branch](https://github.com/AxieFeat/Arc/tree/dev), commit [419619a2ec7147e76d9b696af823d0ecdeb3e684](https://github.com/AxieFeat/Arc/commit/419619a2ec7147e76d9b696af823d0ecdeb3e684), reviewed on **October 23, 2026**. Sources were obtained through Git, rather than reconstructed from README examples or earlier discussions. Arc was not built or run while preparing this specification. Findings refer to inspected code; inferences are identified separately.


## How to assign this work


1. Give GPT 6.1 Sol this entire directory and access to an Arc working copy.
2. Use [01_MASTER_PROMPT.md](01_MASTER_PROMPT.md) as the main instruction.
3. Ask it to follow [12_IMPLEMENTATION_PLAN.md](12_IMPLEMENTATION_PLAN.md) sequentially.
4. Keep all documents in the project context. If they must be supplied in parts, start with the master prompt, Arc analysis, architecture and Surface documents.
5. Treat [13_API_EXAMPLES.md](13_API_EXAMPLES.md) as a **target API to implement**, not as existing Arc methods.


Executing this assignment must produce a working library, Arc changes, demos and checks, rather than another architecture document. This document set is the specification; the library has not yet been implemented here.


## Documents


| File | Purpose |
| --- | --- |
| [01_MASTER_PROMPT.md](01_MASTER_PROMPT.md) | Main instruction, constraints and deliverables |
| [02_ARC_ANALYSIS_AND_STYLE.md](02_ARC_ANALYSIS_AND_STYLE.md) | Verified Arc findings, code/package style and source links |
| [03_ARCHITECTURE_AND_MODULES.md](03_ARCHITECTURE_AND_MODULES.md) | Modules, dependency boundaries, contracts and element tree |
| [04_SURFACE_LAYOUT_RESPONSIVE.md](04_SURFACE_LAYOUT_RESPONSIVE.md) | Surface, coordinates, measurement, responsiveness and overlays |
| [05_KOTLIN_DSL_AND_STATE.md](05_KOTLIN_DSL_AND_STATE.md) | DSL, reactive state, extension and identity |
| [06_RENDERING_AND_APPEARANCE.md](06_RENDERING_AND_APPEARANCE.md) | Geometry, drawing, styling, skins and backend capabilities |
| [07_TEXT_AND_OVERFLOW.md](07_TEXT_AND_OVERFLOW.md) | Fonts, text layout, ellipsis and hover scrolling |
| [08_INPUT_FOCUS_AND_ACCESSIBILITY.md](08_INPUT_FOCUS_AND_ACCESSIBILITY.md) | Input, routing, focus, editing and accessibility |
| [09_ARC_EXTENSION_PLAN.md](09_ARC_EXTENSION_PLAN.md) | Required Arc changes, rationale and acceptance checks |
| [10_COMPONENTS_AND_EXTENSIBILITY.md](10_COMPONENTS_AND_EXTENSIBILITY.md) | Components, content slots and custom elements |
| [11_LIFECYCLE_PERFORMANCE_AND_TESTS.md](11_LIFECYCLE_PERFORMANCE_AND_TESTS.md) | Lifecycle, resources, performance and verification |
| [12_IMPLEMENTATION_PLAN.md](12_IMPLEMENTATION_PLAN.md) | Stages, readiness, priorities and delivery |
| [13_API_EXAMPLES.md](13_API_EXAMPLES.md) | Consistent target API examples |
| [14_REQUIREMENTS_MATRIX.md](14_REQUIREMENTS_MATRIX.md) | Requirements traceability and acceptance scenarios |


## Normative terms


- **MUST / REQUIRED**: required for the final version 1.0 delivered under this assignment.
- **Target API**: proposed names and call shapes. They may be refined before implementation while preserving behavior, abstraction boundaries and consistent examples.
- **Optional feature**: a separate later stage. Its absence does not block 1.0; the architecture must allow it.
- **Capability**: an explicitly declared feature of a particular backend. An ignored operation does not count as support.


Required features cannot be postponed merely because they need Arc changes. The owner's explicit instructions take precedence. Within this set, precise rules in the relevant technical document take precedence over abbreviated examples. Record significant decisions in ADRs and update the affected documents.