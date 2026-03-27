# Troubleshooting Runbook

## Quick Diagnosis Checklist

1. Check service health: `curl http://<host>:<port>/actuator/health`
2. Check recent logs: `docker compose logs --tail=100 <service>`
3. Check infrastructure: PostgreSQL, Redis, NATS, Keycloak all healthy?
4. Check NATS monitoring: `curl http://localhost:8222/varz`
5. Check resource usage: `docker stats` or `kubectl top pods`

## Log Locations and Format

### Docker Compose

```bash
# View logs for a specific service
docker compose logs squadron-orchestrator
docker compose logs --tail=200 --follow squadron-agent

# View all service logs
docker compose --profile services logs

# Export logs to file
docker compose logs squadron-orchestrator > orchestrator.log 2>&1
```

### Kubernetes

```bash
# View pod logs
kubectl -n squadron-system logs deployment/squadron-orchestrator

# View previous container logs (after a crash)
kubectl -n squadron-system logs deployment/squadron-orchestrator --previous

# Stream logs
kubectl -n squadron-system logs -f deployment/squadron-agent

# View logs for all pods of a service
kubectl -n squadron-system logs -l app=squadron-orchestrator --all-containers
```

### Log Format

Squadron services use structured JSON logging in production:

```json
{
  "timestamp": "2026-03-27T10:15:30.123Z",
  "level": "INFO",
  "logger": "com.squadron.orchestrator.engine.WorkflowEngine",
  "message": "Task state transition completed",
  "tenantId": "uuid",
  "taskId": "uuid",
  "fromState": "PLANNING",
  "toState": "PROPOSE_CODE",
  "traceId": "abc123",
  "spanId": "def456"
}
```

Key fields for filtering:
- `tenantId` — isolate logs for a specific tenant
- `taskId` — trace a specific task through the system
- `traceId` — trace a request across services
- `level` — ERROR, WARN, INFO, DEBUG

## Service Health Checks

### Check All Services

```bash
#!/bin/bash
services=(
  "identity:8081"
  "config:8082"
  "orchestrator:8083"
  "platform:8084"
  "agent:8085"
  "workspace:8086"
  "git:8087"
  "review:8088"
  "notification:8089"
)

for svc in "${services[@]}"; do
  name="${svc%%:*}"
  port="${svc##*:}"
  status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health")
  if [ "$status" = "200" ]; then
    echo "[OK]   ${name} (port ${port})"
  else
    echo "[FAIL] ${name} (port ${port}) - HTTP ${status}"
  fi
done
```

### Check Infrastructure

```bash
# PostgreSQL
docker compose exec postgres pg_isready -U squadron
# Expected: /var/run/postgresql:5432 - accepting connections

# Redis
docker compose exec redis redis-cli ping
# Expected: PONG

# NATS
curl -s http://localhost:8222/healthz
# Expected: ok

# Keycloak
curl -s http://localhost:8080/health/ready | jq .status
# Expected: "UP"
```

## Common Issues and Solutions

### 1. Database Connection Issues

#### Symptom: "Connection refused" on startup

**Cause**: PostgreSQL not ready when service starts.

**Diagnosis**:
```bash
docker compose ps postgres
docker compose logs postgres | tail -20
```

**Fix**: Ensure the health check is working and the service has `depends_on` with
`condition: service_healthy`.

#### Symptom: "FATAL: too many connections for role"

**Cause**: Connection pool exhaustion.

**Diagnosis**:
```bash
# Check active connections
docker compose exec postgres psql -U squadron -c \
  "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"

# Check PgBouncer stats
docker compose exec pgbouncer psql -p 6432 -U squadron pgbouncer -c "SHOW pools;"
```

**Fix**:
1. Check HikariCP pool settings in `application.yml` (default: 10 per service)
2. Increase PgBouncer `default_pool_size`
3. Check for connection leaks: look for unclosed transactions
4. Increase PostgreSQL `max_connections`

#### Symptom: "Could not obtain JDBC connection" / slow queries

**Cause**: Database performance issues.

**Diagnosis**:
```bash
# Check slow queries
docker compose exec postgres psql -U squadron -c \
  "SELECT pid, now() - pg_stat_activity.query_start AS duration, query
   FROM pg_stat_activity
   WHERE (now() - pg_stat_activity.query_start) > interval '5 seconds'
   AND state != 'idle';"

# Check table bloat
docker compose exec postgres psql -U squadron -c \
  "SELECT relname, n_dead_tup, last_vacuum, last_autovacuum
   FROM pg_stat_user_tables ORDER BY n_dead_tup DESC LIMIT 10;"
```

**Fix**:
1. Run `VACUUM ANALYZE` on bloated tables
2. Check for missing indexes
3. Review `deploy/loadtest/tuning/postgresql-tuning.conf` for tuning guidance
4. Enable `log_min_duration_statement = 500` to log slow queries

### 2. NATS Connection Issues

#### Symptom: "NATS connection lost" in service logs

**Diagnosis**:
```bash
# Check NATS server status
curl -s http://localhost:8222/varz | jq '{connections, subscriptions, slow_consumers}'

# Check connections
curl -s http://localhost:8222/connz | jq '.connections[] | {name, ip, subscriptions}'

# Check JetStream status
curl -s http://localhost:8222/jsz | jq '{streams, consumers, messages, bytes}'
```

**Fix**:
1. Restart NATS: `docker compose restart nats`
2. Check `max_connections` in NATS config
3. Check for slow consumers: `curl http://localhost:8222/connz?state=any | jq '.connections[] | select(.slow_consumer)'`
4. Increase NATS `max_pending` for slow consumers

#### Symptom: Events not being delivered / processed

**Diagnosis**:
```bash
# Check JetStream stream info
curl -s http://localhost:8222/jsz?streams=true | jq '.account_details[].stream_detail[]'

# Look for dead letter messages in service logs
docker compose logs squadron-notification | grep -i "dead_letter\|failed\|retry"
```

**Fix**:
1. Check consumer acknowledgment: consumers must ACK messages
2. Check for dead letter queue entries in the database
3. Verify NATS subject names match between publishers and subscribers
4. Restart the affected service

### 3. Authentication/Authorization Issues

#### Symptom: 401 Unauthorized on all requests

**Diagnosis**:
```bash
# Check if Keycloak is up
curl -s http://localhost:8080/health/ready

# Check JWKS endpoint (used for JWT validation)
curl -s http://localhost:8081/api/auth/jwks | jq .

# Test login
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@squadron.dev","password":"password"}' | jq .
```

**Fix**:
1. Ensure Keycloak realm and client are configured
2. Check clock skew between services (JWT validation is time-sensitive)
3. Verify `KEYCLOAK_URL` and `KEYCLOAK_CLIENT_SECRET` environment variables
4. Check that the Gateway is forwarding the `Authorization` header

#### Symptom: 403 Forbidden for a specific operation

**Diagnosis**: Check the user's role in the JWT token:
```bash
# Decode JWT (replace with actual token)
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq .
```

**Fix**: Ensure the user has the required role (`@PreAuthorize` annotations in
controllers define required roles).

### 4. Agent/AI Issues

#### Symptom: Agent chat returns errors or empty responses

**Diagnosis**:
```bash
# Check agent service logs
docker compose logs squadron-agent | grep -i "error\|exception\|timeout"

# Check OpenAI API connectivity
curl -s https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY" | jq '.data[:3]'

# Check Ollama connectivity
curl -s http://localhost:11434/api/tags | jq .
```

**Fix**:
1. Verify `OPENAI_API_KEY` is set and valid
2. Check token budget limits in squadron config
3. For Ollama: ensure the model is pulled (`docker compose logs squadron-ollama-pull`)
4. Check workspace is accessible for tool-calling agents

#### Symptom: Agent response very slow

**Cause**: AI model latency, token limit too high, or workspace I/O slow.

**Diagnosis**:
```bash
# Check usage metrics
curl -s http://localhost:8085/api/agent/usage/summary?tenantId=<id> \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Fix**:
1. Use a faster model (e.g., smaller Ollama model for local dev)
2. Reduce `max_tokens` in squadron config
3. Check workspace container performance
4. Check if the agent is stuck in a tool-calling loop (max 25 iterations for coding)

#### Symptom: Token limit exceeded

**Diagnosis**:
```bash
docker compose logs squadron-agent | grep -i "token\|budget\|limit"
```

**Fix**:
1. Increase `token_budget_daily` in squadron config
2. Check per-conversation token usage in the `conversations` table
3. Consider using a smaller model or lower `max_tokens`

### 5. Workspace Issues

#### Symptom: Workspace creation fails

**Diagnosis**:
```bash
# Check workspace service logs
docker compose logs squadron-workspace | grep -i "error\|fail"

# Check Docker socket access
docker compose exec squadron-workspace ls -la /var/run/docker.sock
```

**Fix**:
1. Ensure Docker socket is mounted in the workspace service container
2. Check resource limits (CPU, memory, disk) for new workspaces
3. Verify the base image exists: `docker images | grep workspace`
4. For Kubernetes: check pod creation permissions in the `squadron-workspaces` namespace

#### Symptom: Workspace commands hang

**Diagnosis**:
```bash
# Check running containers
docker ps --filter name=squadron-ws

# Check container resource usage
docker stats --no-stream --filter name=squadron-ws
```

**Fix**:
1. Check workspace resource limits (may need more CPU/memory)
2. Check for long-running processes in the workspace
3. Workspace cleanup scheduler should terminate stale workspaces

### 6. Notification Delivery Failures

#### Symptom: Notifications not being sent

**Diagnosis**:
```bash
# Check notification service logs
docker compose logs squadron-notification | grep -i "error\|fail\|retry"

# Check NATS subscriptions for notification service
curl -s http://localhost:8222/subsz | jq '.subscriptions_list[] | select(.subject | contains("notification"))'
```

**Fix**:
1. Check email config: `MAIL_HOST` and `MAIL_PORT` environment variables
2. Check Slack/Teams webhook URLs in notification preferences
3. Check for DEAD_LETTER notifications in the database
4. Restart notification service to re-establish NATS subscriptions

## Collecting Diagnostic Information

When escalating an issue, collect the following:

```bash
#!/bin/bash
# Diagnostic bundle collection script
DIAG_DIR="squadron-diag-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DIAG_DIR"

# Service health
for port in 8081 8082 8083 8084 8085 8086 8087 8088 8089; do
  curl -s "http://localhost:${port}/actuator/health" > "$DIAG_DIR/health-${port}.json" 2>&1
done

# Container status
docker compose ps > "$DIAG_DIR/docker-ps.txt" 2>&1
docker stats --no-stream > "$DIAG_DIR/docker-stats.txt" 2>&1

# Recent logs (last 500 lines per service)
docker compose --profile services logs --tail=500 > "$DIAG_DIR/all-logs.txt" 2>&1

# NATS info
curl -s http://localhost:8222/varz > "$DIAG_DIR/nats-varz.json" 2>&1
curl -s http://localhost:8222/connz > "$DIAG_DIR/nats-connz.json" 2>&1
curl -s http://localhost:8222/jsz > "$DIAG_DIR/nats-jsz.json" 2>&1

# PostgreSQL connection info
docker compose exec -T postgres psql -U squadron -c \
  "SELECT datname, numbackends, xact_commit, xact_rollback, conflicts, deadlocks
   FROM pg_stat_database WHERE datname LIKE 'squadron_%';" > "$DIAG_DIR/pg-stats.txt" 2>&1

# Package it up
tar czf "${DIAG_DIR}.tar.gz" "$DIAG_DIR"
echo "Diagnostic bundle: ${DIAG_DIR}.tar.gz"
```

## Performance Troubleshooting

### Identifying Slow Endpoints

```bash
# Check Prometheus metrics for slow endpoints
curl -s http://localhost:8083/actuator/prometheus | \
  grep "http_server_requests_seconds" | \
  grep "quantile=\"0.95\""
```

### Database Performance

```bash
# Check pg_stat_statements for slow queries (requires extension)
docker compose exec postgres psql -U squadron -c \
  "SELECT query, calls, total_exec_time/calls as avg_time_ms, rows
   FROM pg_stat_statements
   ORDER BY total_exec_time DESC LIMIT 10;"

# Check index usage
docker compose exec postgres psql -U squadron -d squadron_orchestrator -c \
  "SELECT relname, seq_scan, idx_scan, n_live_tup
   FROM pg_stat_user_tables ORDER BY seq_scan DESC LIMIT 10;"
```

### JVM Memory Analysis

```bash
# Check JVM heap usage via actuator
curl -s http://localhost:8083/actuator/metrics/jvm.memory.used | jq .

# For deeper analysis, trigger a heap dump
curl -X POST http://localhost:8083/actuator/heapdump -o heap.hprof
```
