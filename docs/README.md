# E.R.R.O. documentation

Start with [Project context](project-context.md). It records who is building
E.R.R.O., which ideas are tentative, and the limits of the current website.

[Infrastructure](infrastructure.md) covers the Java backend, Docker Compose,
PostgreSQL, Redis, local MinIO, and the planned switch to Amazon S3.

[Electrical knowledge](knowledge.md) covers retrieval, migrations, source licensing,
admin ingestion/debugging, calculations, catalog validation and Railway rollout.

[Chat](chat.md) covers the AI request flow, API contract, DeepSeek configuration,
frontend behavior, deployment settings, and verification.

[Homepage](homepage.md) covers the introduction, founder photos and draft bios,
top navigation, email contact, expandable AI corner, and frontend verification.

[Web proxy](web-proxy.md) covers the Nginx image, private API forwarding, runtime
variables, local production/development modes, and proxy regression tests.

[Railway setup](railway.md) walks through adding the backend, PostgreSQL 18, and
Redis beside the existing web service, with copy-paste variables, networking,
deployment order, and troubleshooting. S3 is disabled there until it is needed.

Update that context when Eric and Robin make a decision about the project's
direction. Keep possibilities distinct from agreed requirements.

## Documentation policy

Eric wants all new work documented as part of the change, including features,
infrastructure, dependencies, configuration options, and development workflows.
Describe how to use the change, why consequential choices were made, and what
was verified or remains untested. Update existing documents when behavior
changes, link new documents here, and keep the root README's setup instructions
consistent. Record scope and product decisions in the project context.
