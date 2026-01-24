# Ollama with Docker — Step-by-step (command by command)

This guide shows how to install and run **Ollama** using Docker, then pull a model and test it via HTTP.

> Assumes Linux/macOS. Windows works too if you use Docker Desktop + WSL2.

---

## 0) Prerequisites

### Install Docker
- **Linux:** install Docker Engine + Compose plugin
- **macOS/Windows:** install Docker Desktop

Verify:
```bash
docker --version
docker compose version
```

---

## 1) Create a project folder

```bash
mkdir -p ollama-docker
cd ollama-docker
```

---

## 2) Create a Docker Compose file

Create `docker-compose.yml`:

```bash
cat > docker-compose.yml <<'YAML'
services:
  ollama:
    image: ollama/ollama:latest
    container_name: ollama
    environment:
      # Important: allow access from other containers and from host (if ports are mapped)
      - OLLAMA_HOST=0.0.0.0:11434
    volumes:
      # Persist downloaded models
      - ollama:/root/.ollama
    ports:
      # Expose Ollama API on the host (optional but convenient for testing)
      - "11434:11434"
    restart: unless-stopped

volumes:
  ollama:
YAML
```

---

## 3) Start Ollama

```bash
docker compose up -d
```

Confirm container is running:
```bash
docker ps --filter "name=ollama"
```

View logs:
```bash
docker logs -f ollama
```

---

## 4) Test the API is reachable

List installed models (may be empty at first):
```bash
curl http://localhost:11434/api/tags
```

If you get a JSON response, Ollama is up.

---

## 5) Pull / run a model inside the container

### Option A — run a model (pulls automatically if missing)
```bash
docker exec -it ollama ollama run gemma3
docker exec -it ollama ollama run gemma:latest
```

You should see an interactive prompt. Type something, then exit with `Ctrl+C`.

### Option B — pull without running
```bash
docker exec -it ollama ollama pull gemma3
```

Check models again:
```bash
curl http://localhost:11434/api/tags
```

---

## 6) Generate text (non-streaming)

```bash
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma3",
    "prompt": "Say hello in Portuguese",
    "stream": false
  }'
```

---

## 7) Chat (non-streaming)

```bash
curl -X POST http://localhost:11434/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma3",
    "stream": false,
    "messages": [
      { "role": "user", "content": "Explain Docker in one sentence." }
    ]
  }'
```

---

## 8) Chat (streaming, NDJSON)

Ollama streams **one JSON object per line**.

```bash
curl -N -X POST http://localhost:11434/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma3",
    "stream": true,
    "messages": [
      { "role": "user", "content": "Count from 1 to 10, slowly." }
    ]
  }'
```

Tips:
- `-N` disables curl buffering so you see tokens as they arrive.
- Each line is JSON; you can pipe it into `jq` if you want.

---

## 9) If you will call Ollama from another container

Inside the same Compose network, **do not use localhost**. Use the service name:

```bash
curl http://ollama:11434/api/tags
```

(That command must be run from a container that’s on the same Docker network.)

Example:
```bash
docker run --rm --network ollama-docker_default curlimages/curl:8.10.1 \
  http://ollama:11434/api/tags
```

---

## 10) Stop / restart / delete

Stop:
```bash
docker compose down
```

Stop and remove persisted models (WARNING: deletes downloaded models):
```bash
docker compose down -v
```

Restart:
```bash
docker compose restart ollama
```

---

## 11) Troubleshooting

### A) `curl: (7) Failed to connect`
- Ensure container is running:
  ```bash
  docker ps --filter "name=ollama"
  ```
- Ensure port is published:
  ```bash
  docker inspect ollama --format '{{json .HostConfig.PortBindings}}' | jq
  ```
- Check logs:
  ```bash
  docker logs --tail=200 ollama
  ```

### B) Other containers can’t reach Ollama
Make sure `OLLAMA_HOST=0.0.0.0:11434` is set in compose, then:
```bash
docker compose up -d --force-recreate
```

### C) Model name not found
List models you actually have:
```bash
curl http://localhost:11434/api/tags
```
Then use one of those names in `model`.

---

## 12) (Optional) GPU acceleration (NVIDIA on Linux)

If you have an NVIDIA GPU, install **NVIDIA Container Toolkit**, then add GPU support to the service.

Example (Compose v2):
```yaml
services:
  ollama:
    # ...
    deploy:
      resources:
        reservations:
          devices:
            - capabilities: [gpu]
```

You may need to use `--gpus all`-style configuration depending on your Docker setup.
