<h1>Grain Pantry App</h1>

This project is an end-to-end example of building an event-sourced application with agentic features and AI integration using the [Grain](https://github.com/ObneyAI/grain) framework from ObneyAI.

Note: This is an active WIP

## Features

### Core Functionality
- **Pantry Tracking**: Add, update, and remove items with quantities, categories, and expiration dates
- **Shopping Lists**: Manage shopping items with recipe linking and bulk operations (clear completed, move to pantry)
- **Multi-tenant Households**: Secure, isolated data per household with member management
- **Authentication**: JWT-based auth with email verification and password reset

### AI Copilot
- **Conversational Assistant**: Chat interface with context-aware responses about your pantry
- **Executable Actions**: AI suggests and executes pantry/shopping operations with one click
- **Event-Sourced Conversations**: Full conversation history with audit trail
- **Behavior Trees + DSPy**: Type-safe LLM orchestration with chain-of-thought reasoning

### Technical Architecture
- **Event Sourcing + CQRS**: Immutable event log with separate read/write models
- **Polylith Monorepo**: Component-based architecture (Clojure backend, ClojureScript frontend)
- **Modern Stack**: Grain framework, Re-frame state management, shadcn/ui components, Tailwind CSS v4

# Dependencies
- Clojure
- Polylith
- Babashka (optional)
- NPM, Node, ETC
- Open Router Key
- Docker

## Python

### With UV

#### Install the venv

`uv venv --python 3.13 .venv`

#### Activate venv

`source .venv/bin/activate`

#### Install deps

`uv pip install -r requirements.txt`

## JS

### Install node modules

`cd ui/web-app/ && npm install`

# Run Frontend

`cd ui/web-app && npm run dev`

# Run Backend

1. `docker-compose up -d`
2. Start REPL w/ dev alias
2. Eval `development/src/repl_stuff.clj`
3. Eval the `do` form on line 10

# Create a new Grain Service Component

1. `bb scripts/create_component.bb <your-component-name>`
2. Include your component in the root level deps.edn file