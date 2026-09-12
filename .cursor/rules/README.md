# Cursor rules for CyberSixSeven

## Install

Copy every `.mdc` file into `.cursor/rules/` at the repo root:

```bash
mkdir -p <repo>/.cursor/rules && cp *.mdc <repo>/.cursor/rules/
```

The `.mdc` extension is required — a plain `.md` file in `.cursor/rules` is **silently ignored**, because it has no frontmatter to carry `description`, `globs` and `alwaysApply`. Commit the folder; rules are version-controlled and shared.

`.cursorrules` (the single root file) is the legacy format and is not used here.

## How they attach

| Rule | Attaches to |
|---|---|
| `00-global.mdc` | **Every request** (`alwaysApply: true`) |
| `backend-jvm.mdc` | `services/platform-api`, `device-command-service`, `reward-service`, `contracts` |
| `backend-security.mdc` | `services/platform-api` |
| `backend-messaging.mdc` | `platform-api`, `device-command-service`, `contracts` |
| `kotlin-reward-service.mdc` | `services/reward-service` |
| `python-model-gen.mdc` | `services/model-gen-service` |
| `frontend-next.mdc` | `apps/**`, `packages/**` |
| `frontend-auth.mdc` | auth-client, api-client, login pages, layouts |
| `frontend-testing.mdc` | test files and test configs |
| `infra-terraform.mdc` | `infra/terraform` |
| `docker-and-ci.mdc` | compose file, Dockerfiles, workflows, Helm chart |
| `firmware-esp32.mdc` | `firmware/esp32` |

Overlap is intentional — several rules attach at once in `platform-api`, which is where the most can go wrong.

## Relationship to the plan documents

These rules encode the *conventions*. The plan documents still drive the work:

- `07-cursor-prompts.md` — the prompt for the version you are building
- `05-tech-stack.md` — pinned versions, pasted alongside the prompt
- `06-cursor-guardrails.md` — the human-readable source of `00-global.mdc`

Once the rules are installed, `00-global.mdc` is applied automatically and you no longer need to paste `06` by hand.

## Keeping them honest

If you change a pinned version in `05-tech-stack.md`, grep these rules for the old number. Versions appear in both places by design — the rules need them inline to be useful — so they can drift.
