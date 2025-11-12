# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a **Polylith monorepo** that combines Clojure backend services with a ClojureScript/React frontend. The architecture uses:

- **Grain Framework**: Event-sourced microservices framework built on top of Polylith
- **Polylith Architecture**: Components in `components/`, bases in `bases/`, and projects in `projects/`
- **Event Sourcing**: All state changes are captured as events in an event store
- **CQRS Pattern**: Separate command and query handlers
- **Python Integration**: Uses libpython-clj2 for Python interop (DSPy for LLM workflows)

### Key Directories

- `components/`: Reusable Clojure components (foundation components and service components)
  - Foundation components: `crypto`, `crypto-kms`, `email`, `email-ses`, `file-store`, `file-store-s3`, `jwt`, `url-presigner`, `url-presigner-aws`
  - Service components: `user-service`, `pantry-service`, `recipe-service`, `ai-agent-service`
- `bases/`: Application entry points (`web-api`)
- `projects/`: Deployable artifacts (currently just `web-api`)
- `ui/web-app/`: ClojureScript frontend using UIx, shadow-cljs, and shadcn components
- `development/`: Development-only code and REPL utilities
- `docs/`: Detailed documentation (see below)

### Grain Service Components

Grain service components follow a specific structure:
- `interface.clj`: Public API for the component
- `interface/schemas.clj`: Command/event/query schemas using `defschemas` macro
- `core.clj`: Implementation logic
- Each service defines: `commands`, `queries`, and `todo-processors` (event handlers)

The top-level namespace is `ai.obney.workshop` (configured in workspace.edn).

## Quick Start

### Backend Development

1. Start infrastructure:
   ```bash
   docker-compose up -d
   ```
   This starts PostgreSQL (pgvector) and LocalStack (for S3/KMS emulation).

2. Start REPL with dev alias:
   ```bash
   clj -A:dev
   ```

3. In the REPL, evaluate `development/src/repl_stuff.clj` and run the `do` form on line 10:
   ```clojure
   (do
     (def service (service/start))
     (def context (::service/context service))
     (def event-store (:event-store context)))
   ```

4. Stop the service:
   ```clojure
   (service/stop service)
   ```

### Frontend Development

1. Install dependencies:
   ```bash
   cd ui/web-app && npm install
   ```

2. Start dev server (runs shadow-cljs, Tailwind CSS watch, and TypeScript watch):
   ```bash
   npm run dev
   ```
   This starts the dev server on http://localhost:8080 with hot reload.

3. Build for production:
   ```bash
   npm run build
   ```

4. Build variants (with different API base URLs):
   - `npm run build:dev` - Points to http://localhost:8081
   - `npm run build:staging` - Points to staging API
   - `npm run build:prod` - Points to production API

### Creating New Components

Use the Babashka script to create a new Grain service component:

```bash
bb scripts/create_component.bb <component-name>
```

Then add the component to the root `deps.edn` file under the `:dev` alias.

## Key Technologies

- **Clojure 1.12.0**: Backend language
- **shadow-cljs**: ClojureScript build tool
- **UIx**: React wrapper for ClojureScript
- **shadcn/ui**: UI component library (TypeScript components wrapped for ClojureScript)
- **Tailwind CSS v4**: Styling
- **Pedestal**: HTTP server framework
- **Integrant**: System lifecycle management
- **libpython-clj2**: Python interop
- **DSPy**: LLM workflow framework
- **mulog**: Structured logging with CloudWatch EMF publisher support

## System Architecture

The web-api base (`bases/web-api/core.clj`) defines the system configuration using Integrant:

- **Event Store**: Can be configured as `:in-memory` or `:postgres`
- **Event Pubsub**: Uses core.async for event distribution
- **Todo Processors**: Event handlers that react to domain events
- **Context**: Shared context containing event-store, command/query registries, email client, crypto provider, JWT secret
- **Webserver**: Pedestal HTTP server with CORS support
- **Authentication**: Uses JWT tokens stored in HTTP-only cookies

**Important:** The Grain framework auto-generates `/command` and `/query` endpoints by reading the registries. No manual routing needed!

### Request Flow

1. HTTP requests enter through Pedestal interceptors
2. Auth cookie is extracted and validated (`extract-auth-cookie` interceptor)
3. Commands are routed through `command-request-handler`
4. Queries are routed through `query-request-handler`
5. Auth tokens are set/cleared via the `set-auth-cookie` interceptor

## Configuration

Configuration is loaded from `config.edn` using the `config.core/env` var. Key configuration:

- `:webserver/http-port`: HTTP port for web server
- `:jwt-secret`: Secret for signing JWT tokens
- `:app-base`: Base URL for the application
- `:email-from`: From address for emails
- `:openrouter-api-key`: API key for OpenRouter (used with DSPy)

## Python Environment

The project uses Python for LLM-related functionality (DSPy).

### Setup with UV

1. Install venv:
   ```bash
   uv venv --python 3.13 .venv
   ```

2. Activate venv:
   ```bash
   source .venv/bin/activate
   ```

3. Install dependencies:
   ```bash
   uv pip install -r requirements.txt
   ```

## LocalStack Configuration

LocalStack emulates AWS services (S3, KMS) for local development. Configuration is in `config.edn`:

```clojure
{:localstack/enabled true
 :localstack/endpoint "http://localhost:4566"
 :aws/region "us-east-1"
 :kms-key-id "alias/grain-local-key"
 :s3-bucket "grain-files"}
```

When LocalStack mode is enabled, emails are logged via mulog instead of being sent through SES.

See LOCALSTACK.md for detailed setup and testing instructions.

## Polylith Commands

The project uses Polylith for workspace management:

```bash
# Run Polylith CLI
clj -A:poly <command>
```

Common commands:
- `clj -A:poly info`: Show workspace info
- `clj -A:poly check`: Check workspace validity
- `clj -A:poly test`: Run tests

## Important Notes

- The workspace top namespace is `ai.obney.workshop`
- The default profile is `development` (alias `dev`)
- All Grain service components expose their APIs through `interface.clj` files
- The interface namespace convention is `<namespace>.interface`
- Schemas are defined using the Grain `defschemas` macro for central registration
- Only export public APIs through `interface.clj` - keep implementation details in `core/` namespaces

## Detailed Documentation

For comprehensive guides on building features, see the `docs/` directory:

### [Building Features End-to-End](docs/building-features.md)
Complete guide to building features from backend to frontend:
- Backend service component structure (commands, queries, read models, todo processors)
- Frontend integration with Re-frame
- Step-by-step walkthrough of adding new features
- Authentication & authorization patterns
- File location quick reference

### [Building AI Features with Grain + DSPy](docs/building-ai-features.md)
Comprehensive guide to building AI-powered features:
- Architecture overview (Behavior Trees, DSPy, Event Sourcing)
- AI service component structure
- DSPy integration and signatures
- Behavior tree patterns and custom actions
- Frontend integration for AI features
- Troubleshooting and best practices

### [Frontend Development Guide](docs/frontend-guide.md)
Deep dive into ClojureScript/React frontend development:
- Re-frame architecture (effects, events, subscriptions)
- UIx component patterns
- API client integration
- State management best practices

### [Polylith Architecture Guide](docs/polylith-guide.md)
Understanding Polylith patterns in this project:
- Component boundaries and interface/core separation
- Dependency management
- Common pitfalls and how to avoid them
