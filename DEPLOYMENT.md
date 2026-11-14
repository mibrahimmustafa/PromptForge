# Deploying PromptForge to production

Everything below was validated on a real Docker daemon: the image builds, the container reports
`healthy`, the API answers, and the process runs as a non-root user.

---

## 0. Read this first: there is no built-in authentication

PromptForge ships **no authentication and no authorization**. Exposed directly to the internet,
anyone who finds the URL can use it — and if an LLM provider key is configured, they can spend
your money on it.

Choose one of these before going live:

| Option | How | Good for |
|---|---|---|
| **Internal only** | Bind to loopback, reach it over a VPN (WireGuard/Tailscale) or an internal DNS name | Teams (recommended) |
| **HTTP basic auth** | `htpasswd` at nginx — the config is included, just uncomment 3 lines | Small teams, internal tools |
| **Identity-aware proxy** | oauth2-proxy, Cloudflare Access, Azure App Service Easy Auth, Cognito | Corporate SSO |
| **Add auth to the app** | Not implemented in v1.0 — see *Future scope* in the SRD | Public products |

The **safest production configuration is the local rule engine with no provider key**: the app
makes zero outbound calls, so prompt content can never leave the server.

---

## 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17+ (21 recommended) | Only needed to build; the runtime image ships its own JRE |
| Docker + Compose | Docker 24+, Compose v2.24+ | `required: false` in `env_file` needs Compose ≥ 2.24 |
| Nginx | 1.20+ | TLS termination, rate limiting, security headers |
| Linux | any modern distro | Instructions below assume systemd (Ubuntu, Debian, RHEL) |

Nothing else — no database, no migrations, no external state.

---

## 2. Build

### 2.1 The jar (any platform)

```bash
git clone <your-repo> prompt-forge && cd prompt-forge
./mvnw clean package            # runs the 32 tests, then builds the jar
ls -lh target/prompt-forge-1.0.0.jar
```

Maven is not required — the wrapper is committed. On Windows use `mvnw.cmd`.

> **Stop the running service before rebuilding.** The JVM keeps the jar file open, and on
> Windows `mvn clean` then fails with *"The process cannot access the file because it is being
> used by another process"*. Either `sudo systemctl stop prompt-forge` first, or build in a
> container (`docker compose build`), which is unaffected by the running instance.

### 2.2 The container image

```bash
docker build -t prompt-forge:1.0.0 .
docker images prompt-forge:1.0.0
```

- Multi-stage build: `maven:3.9-eclipse-temurin-21` compiles, `eclipse-temurin:21-jre-alpine`
  runs.
- The **tests run inside the image build**, so only tested code can ship.
- Final image ≈ 330 MB, runs as the non-root user `promptforge` (uid 1001).
- The app never writes to disk (read-only root filesystem is safe).

---

## 3. Deployment A — Docker Compose (recommended)

### 3.1 Configure

```bash
cp deploy/prompt-forge.env.example deploy/prompt-forge.env
chmod 600 deploy/prompt-forge.env
$EDITOR deploy/prompt-forge.env          # add the provider keys, if any
```

`deploy/prompt-forge.env` is optional — without it the app runs on the local engine only. It is
git-ignored; **never commit the filled-in file**.

### 3.2 TLS certificates

```bash
mkdir -p deploy/nginx/certs
cp /etc/letsencrypt/live/promptforge.example.com/fullchain.pem deploy/nginx/certs/
cp /etc/letsencrypt/live/promptforge.example.com/privkey.pem  deploy/nginx/certs/
```

(Or mount a certbot volume instead of copying files.)

> The proxy config comes in two flavours, identical except for the upstream address:
> `prompt-forge.conf` uses `127.0.0.1:8080` (bare metal, nginx on the host) and
> `prompt-forge.compose.conf` uses `prompt-forge:8080` (the compose service name).
> `docker-compose.yml` mounts the second one.

### 3.3 Start

```bash
docker compose up -d --build
docker compose ps                      # expect both services healthy
docker compose logs -f prompt-forge
curl -fsS http://127.0.0.1:8080/api/health
```

The stack publishes the app on **loopback only** (`127.0.0.1:8080:8080`); nginx is the only
public entry point on 80/443.

### 3.4 Update to a new version

```bash
git pull
docker compose up -d --build            # recreates the container
docker compose ps
```

---

## 4. Deployment B — bare metal (systemd + nginx)

### 4.1 Install the app

```bash
sudo useradd --system --home /opt/prompt-forge --shell /usr/sbin/nologin promptforge
sudo mkdir -p /opt/prompt-forge /etc/prompt-forge
sudo cp target/prompt-forge-1.0.0.jar /opt/prompt-forge/prompt-forge.jar
sudo cp deploy/prompt-forge.env.example /etc/prompt-forge/prompt-forge.env
sudo chmod 600 /etc/prompt-forge/prompt-forge.env
sudo chown -R promptforge:promptforge /opt/prompt-forge
```

### 4.2 Register the service

```bash
sudo cp deploy/prompt-forge.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now prompt-forge
sudo systemctl status prompt-forge
sudo journalctl -u prompt-forge -f
```

The unit file is hardened: `NoNewPrivileges`, `ProtectSystem=strict`, `PrivateTmp`,
`ProtectHome`, an empty capability set, and `systemd` call filters.

### 4.3 Reverse proxy

```bash
sudo cp deploy/nginx/prompt-forge.conf /etc/nginx/conf.d/prompt-forge.conf
sudo sed -i 's/promptforge.example.com/your-domain.tld/g' /etc/nginx/conf.d/prompt-forge.conf
sudo nginx -t && sudo systemctl reload nginx
```

### 4.4 Optional: HTTP basic auth

```bash
sudo apt install apache2-utils
sudo htpasswd -c /etc/nginx/.htpasswd promptforge
sudo sed -i 's|# auth_basic           "PromptForge";|auth_basic           "PromptForge";|' \
            /etc/nginx/conf.d/prompt-forge.conf
sudo sed -i 's|# auth_basic_user_file /etc/nginx/.htpasswd;|auth_basic_user_file /etc/nginx/.htpasswd;|' \
            /etc/nginx/conf.d/prompt-forge.conf
sudo nginx -t && sudo systemctl reload nginx
```

### 4.5 Upgrade (near-zero downtime)

```bash
sudo cp target/prompt-forge-1.0.0.jar /opt/prompt-forge/prompt-forge.jar.new
sudo systemctl stop prompt-forge
sudo mv /opt/prompt-forge/prompt-forge.jar.new /opt/prompt-forge/prompt-forge.jar
sudo chown promptforge:promptforge /opt/prompt-forge/prompt-forge.jar
sudo systemctl start prompt-forge
curl -fsS http://127.0.0.1:8080/api/health
```

For true zero downtime, run two instances behind nginx on different ports and drain one at a
time (`upstream` with `server 127.0.0.1:8080 backup;`).

---

## 5. Deployment C — Kubernetes

The container image is directly usable. Minimal Deployment + Service:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prompt-forge
spec:
  replicas: 2
  selector:
    matchLabels: { app: prompt-forge }
  template:
    metadata:
      labels: { app: prompt-forge }
    spec:
      containers:
        - name: app
          image: your-registry/prompt-forge:1.0.0
          ports: [{ containerPort: 8080 }]
          env:
            - { name: SERVER_PORT, value: "8080" }
            - { name: JAVA_OPTS, value: "-XX:MaxRAMPercentage=75" }
            # Secrets come from a Secret, never from the manifest:
            # - name: ANTHROPIC_API_KEY
            #   valueFrom: { secretKeyRef: { name: prompt-forge-secrets, key: anthropic-api-key } }
          readinessProbe:
            httpGet: { path: /api/health, port: 8080 }
            initialDelaySeconds: 15
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /api/health, port: 8080 }
            initialDelaySeconds: 40
            periodSeconds: 20
          resources:
            requests: { memory: "256Mi", cpu: "100m" }
            limits:   { memory: "768Mi", cpu: "1000m" }
          securityContext:
            runAsNonRoot: true
            runAsUser: 1001
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: { drop: ["ALL"] }
---
apiVersion: v1
kind: Service
metadata:
  name: prompt-forge
spec:
  selector: { app: prompt-forge }
  ports: [{ port: 80, targetPort: 8080 }]
```

Then put an Ingress (nginx-ingress / Traefik) in front for TLS, and apply the same
`auth` + `rate-limit` rules from `deploy/nginx/prompt-forge.conf`.

---

## 6. Configuration reference

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `LLM_PROVIDER` | `auto` | `auto` \| `anthropic` \| `openai` \| `gemini` |
| `ANTHROPIC_API_KEY` | *(empty)* | Enables the Anthropic engine |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | Anthropic model id |
| `OPENAI_API_KEY` | *(empty)* | Enables the OpenAI engine |
| `OPENAI_MODEL` | `gpt-5.5` | OpenAI model id |
| `GEMINI_API_KEY` | *(empty)* | Enables the Gemini engine |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Gemini model id |
| `LLM_MAX_TOKENS` | `4000` | Max tokens requested from the model |
| `LLM_TIMEOUT_SECONDS` | `60` | Provider request timeout |
| `JAVA_OPTS` | see Dockerfile | JVM flags |
| `FORWARD_HEADERS_STRATEGY` | `framework` | Honour `X-Forwarded-*` from the proxy |

**With no provider key set**, the app still works fully — the local rule engine needs neither a
key nor a network.

---

## 7. Security checklist

- [ ] The app is **not** directly exposed to the internet (loopback binding + proxy).
- [ ] Authentication is in place (VPN, basic auth, or an identity-aware proxy).
- [ ] TLS is terminated with a valid certificate; HTTP redirects to HTTPS.
- [ ] `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options` are set.
- [ ] `deploy/prompt-forge.env` is `chmod 600`, owned by root, and **not** in git.
- [ ] Provider keys are absent unless the LLM engine is actually needed.
- [ ] `client_max_body_size` is small (1 MB) — prompts are text.
- [ ] Rate limiting is on `/api/reshape` so one client cannot drain the key.
- [ ] The container runs as uid 1001 with a read-only root filesystem.
- [ ] `docker compose logs` is rotated (already configured: 10 MB × 5 files).
- [ ] `/api/health` is reachable by the orchestrator but need not be public.

---

## 8. Monitoring

| Signal | Where | Action |
|---|---|---|
| Liveness | `GET /api/health` → `{"status":"UP"}` | Container health check, k8s probes |
| Availability | nginx access log, 5xx rate | Alert on sustained 5xx |
| Provider cost | provider dashboard | The rate limit bounds this |
| Latency | `docker compose logs` timing | Investigate if p95 > 2 s |
| Disk | `docker system df` | Logs are capped; prune old images |

---

## 9. CI/CD (GitHub Actions)

`.github/workflows/ci.yml` builds and tests on every push, then publishes the image to GHCR.
Add the `packages: write` permission and set `IMAGE` in the workflow variables.

```bash
# Deploy from CI to a host
ssh deploy@server 'cd /srv/prompt-forge && docker compose pull && docker compose up -d'
```

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Container exits immediately | `JAVA_OPTS` malformed, or the jar is missing | `docker compose logs prompt-forge` |
| Health check never passes | App still starting (JVM + Spring boot ≈ 4 s) | Raise `start_period` to 40 s |
| UI loads but the LLM engine falls back | No provider key, or the key is invalid | Check `notes` in the response; verify the env var reached the container |
| 502 from nginx | App not listening on the port nginx expects | Check `SERVER_PORT` and the `upstream` block |
| Client IP always `127.0.0.1` | `X-Forwarded-For` not honoured | Set `FORWARD_HEADERS_STRATEGY=framework` |
| Requests hang on `/api/reshape` | Provider is slow | `LLM_TIMEOUT_SECONDS` and nginx `proxy_read_timeout` (120 s) |
| `403` from nginx | Basic auth is on | `htpasswd` file path/permissions |

Useful commands:

```bash
docker compose ps
docker compose logs -f --tail=100 prompt-forge
docker exec -it prompt-forge sh
curl -fsS http://127.0.0.1:8080/api/meta | jq .
sudo journalctl -u prompt-forge -n 200 --no-pager
```

---

## 11. What was verified

The following was executed against a real Docker daemon, not merely written down:

| Check | Result |
|---|---|
| `docker build -t prompt-forge:1.0.0 .` | Builds; the 32 tests run inside the build stage |
| `docker run` + `docker ps` | `Up (healthy)` — the image health check passes |
| `GET /api/health` in the container | `{"status":"UP"}` |
| `POST /api/reshape` in the container | Returns a scored analysis |
| `docker exec … id` | `uid=1001(promptforge)` — non-root |
| Image size | ≈ 330 MB (Alpine JRE) |
| `docker compose config` | Valid, with or without the optional env file |
| `docker compose up -d` (app + nginx) | Both services start; nginx waits for the app to be healthy |
| HTTPS through nginx | `/api/health` → 200, `/` → 200 (6 570 bytes), `POST /api/reshape` → scored result |
| HTTP → HTTPS | `301` redirect |
| `nginx -t` on the shipped config | `test is successful` |
| `ci.yml` YAML parse | Valid |

---

## 12. Related documents

- [`README.md`](README.md) — what the app does, quick start, API
- [`docs/SRD.md`](docs/SRD.md) — requirements, data model, traceability
- [`architecture.json`](architecture.json) — machine-readable architecture
