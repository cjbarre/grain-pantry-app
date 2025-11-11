FROM clojure:temurin-21-tools-deps-bullseye-slim

# Install dependencies
RUN apt-get update && apt-get install -y python3 python3-pip libpq-dev
RUN pip3 install uv

# Setup app directory
RUN mkdir -p /usr/src/app

# Create Python virtual environment
WORKDIR /usr/src/app
RUN uv venv --python 3.13 .venv
RUN . .venv/bin/activate

COPY requirements.txt requirements.txt

RUN uv pip install --no-cache-dir -r requirements.txt

WORKDIR /usr/src/app

# Copy clojure code
COPY . /usr/src/app/
COPY python  /usr/src/app/projects/web-api/python

# Install clojure dependencies
WORKDIR /usr/src/app/projects/web-api
RUN clojure -P

CMD ["clojure", "-M:run"]