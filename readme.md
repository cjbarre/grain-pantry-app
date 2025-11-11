<img src="logo.png" width="30%" alt="Polylith" id="logo">

The Polylith documentation can be found here:

- The [high-level documentation](https://polylith.gitbook.io/polylith)
- The [poly tool documentation](https://cljdoc.org/d/polylith/clj-poly/CURRENT)
- The [RealWorld example app documentation](https://github.com/furkan3ayraktar/clojure-polylith-realworld-example-app)

You can also get in touch with the Polylith Team on [Slack](https://clojurians.slack.com/archives/C013B7MQHJQ).

<h1>Grain Pantry App</h1>

This project is an end-to-end example of building an event-sourced application with agentic features and AI integration using the [Grain](https://github.com/ObneyAI/grain) framework from ObneyAI.

Note: This is an active WIP

# Dependencies
- Clojure
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