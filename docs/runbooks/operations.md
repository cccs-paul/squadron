# Operations Runbook

## Daily Operations Checklist

### Morning Health Check

1. **Verify all services are running**:
   ```bash
   # Docker Compose
   docker compose --profile services ps

   # Kubernetes
   kubectl -n squadron-system get pods
   kubectl -n squadron-infra get pods
   ```

2. **Check service health endpoints**:
   ```bash
   for port in 8081 8082 8083 8084 8085 8086 8087 8088 8089; do
     status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${port}/actuator/health")
     echo "Port ${port}: ${status}"
   done
   ```

3. **Review error logs from the last 24 hours**:
   ```bash
   docker compose --profile services logs --since 24h | grep -i "error\|exception" | head -50
   ```

4. **Check NATS health and pending messages**:
   ```bash
   curl -s http://localhost:8222/jsz?streams=true | jq '.account_details[].stream_detail[] | {name: .name, messages: .state.messages, consumers: .state.consumer_count}'
   ```

5. **Check database connection counts**:
   ```bash
   docker compose exec postgres psql -U squadron -c \
     "SELECT datname, numbackends FROM pg_stat_database WHERE datname LIKE 'squadron_%';"
   ```

6. **Check disk usage**:
   ```bash
   docker system df
   # For Kubernetes:
   kubectl -n squadron-system exec deploy/squadron-orchestrator -- df -h /
   ```

### Weekly Tasks

- Review and clear old DEAD_LETTER notifications
- Check Flyway migration status across services
- Review token usage reports for unusual patterns
- Verify backup integrity (restore test)
- Review and rotate any expiring credentials

### Monthly Tasks

- Full backup restore test
- Security audit: check for outdated dependencies
- Review and update PgBouncer/PostgreSQL tuning parameters
- Review HPA scaling history and adjust thresholds
- Clean up old workspaces and container images

## Backup and Restore

### Database Backup

#### Automated Backup (recommended for production)

```bash
#!/bin/bash
# backup-databases.sh - Run via cron: 0 2 * * * /opt/squadron/backup-databases.sh
set -euo pipefail

BACKUP_DIR="/var/backups/squadron"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RETENTION_DAYS=30
PG_HOST="localhost"
PG_PORT="5432"
PG_USER="squadron"

mkdir -p "${BACKUP_DIR}"

DATABASES=(
  squadron_identity
  squadron_config
  squadron_orchestrator
  squadron_platform
  squadron_agent
  squadron_workspace
  squadron_git
  squadron_review
  squadron_notification
)

for db in "${DATABASES[@]}"; do
  echo "Backing up ${db}..."
  pg_dump -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" \
    --format=custom --compress=9 \
    "${db}" > "${BACKUP_DIR}/${db}-${TIMESTAMP}.dump"
done

# Also backup globals (roles, tablespaces)
pg_dumpall -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" \
  --globals-only > "${BACKUP_DIR}/globals-${TIMESTAMP}.sql"

# Clean up old backups
find "${BACKUP_DIR}" -name "*.dump" -mtime +"${RETENTION_DAYS}" -delete
find "${BACKUP_DIR}" -name "*.sql" -mtime +"${RETENTION_DAYS}" -delete

echo "Backup complete: ${BACKUP_DIR}/*-${TIMESTAMP}.*"
```

#### Manual Backup

```bash
# Backup a single database
pg_dump -h localhost -U squadron --format=custom --compress=9 \
  squadron_orchestrator > squadron_orchestrator-backup.dump

# Backup all databases
pg_dumpall -h localhost -U squadron > all-databases.sql
```

### Database Restore

```bash
# Restore a single database
# WARNING: This drops and recreates the database
dropdb -h localhost -U squadron squadron_orchestrator
createdb -h localhost -U squadron squadron_orchestrator
pg_restore -h localhost -U squadron -d squadron_orchestrator \
  squadron_orchestrator-backup.dump

# Restore from SQL dump
psql -h localhost -U squadron < all-databases.sql
```

### Redis Backup

```bash
# Trigger RDB snapshot
docker compose exec redis redis-cli BGSAVE

# Copy the dump file
docker compose cp redis:/data/dump.rdb ./redis-backup.rdb

# Restore: stop Redis, replace dump.rdb, restart
docker compose stop redis
docker compose cp ./redis-backup.rdb redis:/data/dump.rdb
docker compose start redis
```

### NATS JetStream Backup

```bash
# Export stream data
nats stream backup STREAM_NAME /tmp/nats-backup/

# Restore stream data
nats stream restore STREAM_NAME /tmp/nats-backup/
```

## Scaling Services

### Docker Compose Scaling

```bash
# Scale a specific service (limited usefulness without a load balancer)
docker compose --profile services up -d --scale squadron-agent=3

# Note: Docker Compose scaling requires external load balancer configuration
# For production scaling, use Kubernetes with HPA
```

### Kubernetes Horizontal Pod Autoscaler (HPA)

```yaml
# Example HPA for the Agent Service
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: squadron-agent
  namespace: squadron-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: squadron-agent
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
```

### Recommended Scaling Configuration

| Service | Min Replicas | Max Replicas | CPU Target | Notes |
|---|---|---|---|---|
| Gateway | 2 | 5 | 70% | Stateless, scales easily |
| Identity | 2 | 4 | 70% | Auth is critical path |
| Config | 2 | 3 | 60% | Low traffic, mostly cached |
| Orchestrator | 2 | 6 | 70% | Core workflow engine |
| Platform | 2 | 4 | 60% | External API calls |
| Agent | 3 | 10 | 70% | Most resource-intensive |
| Workspace | 2 | 5 | 60% | Manages containers |
| Git | 2 | 4 | 60% | Git operations |
| Review | 2 | 4 | 60% | Review orchestration |
| Notification | 2 | 3 | 50% | Event processing |

### Manual Scaling (Kubernetes)

```bash
# Scale up
kubectl -n squadron-system scale deployment/squadron-agent --replicas=5

# Scale down
kubectl -n squadron-system scale deployment/squadron-agent --replicas=2

# Check current replica count
kubectl -n squadron-system get deployment squadron-agent
```

## Rolling Updates

### Kubernetes Rolling Update

```bash
# Update image tag
kubectl -n squadron-system set image deployment/squadron-orchestrator \
  squadron-orchestrator=registry.example.com/squadron/squadron-orchestrator:0.2.0

# Monitor rollout
kubectl -n squadron-system rollout status deployment/squadron-orchestrator

# Rollback if issues
kubectl -n squadron-system rollout undo deployment/squadron-orchestrator

# View rollout history
kubectl -n squadron-system rollout history deployment/squadron-orchestrator
```

### Update Strategy (Helm values)

```yaml
# Recommended for all Squadron services
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1          # One extra pod during update
    maxUnavailable: 0    # Never have fewer than desired replicas

# Pod Disruption Budget
podDisruptionBudget:
  minAvailable: 1        # Always keep at least 1 pod running
```

### Docker Compose Update

```bash
# Pull new images
docker compose --profile services pull

# Recreate containers with new images (zero-downtime not guaranteed)
docker compose --profile services up -d --force-recreate

# Update a single service
docker compose up -d --force-recreate squadron-orchestrator
```

## Database Migration Procedures

### Running Migrations

Flyway migrations run automatically on service startup. For manual control:

```bash
# Check migration status
docker compose exec squadron-orchestrator \
  java -cp app.jar org.flywaydb.commandline.Main info

# In Kubernetes
kubectl -n squadron-system exec deploy/squadron-orchestrator -- \
  java -cp app.jar org.flywaydb.commandline.Main info
```

### Migration Best Practices

1. **Never modify** an applied migration file
2. **Always test** migrations in a staging environment first
3. **Make migrations idempotent** where possible (use `IF NOT EXISTS`)
4. **Back up the database** before applying migrations
5. **Use forward-only migrations** — avoid rollback migrations
6. Name format: `V{version}__{description}.sql` (e.g., `V3__add_merge_method.sql`)

### Emergency Migration Rollback

If a migration fails:
1. Fix the migration SQL
2. Run `flyway repair` to fix the schema history
3. Re-run the migration

```bash
# Connect to the service database and check Flyway history
psql -U squadron -d squadron_orchestrator -c \
  "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

## Monitoring and Alerting

### Prometheus Metrics

All Squadron services expose metrics at `/actuator/prometheus`. Key metrics:

| Metric | Description | Alert Threshold |
|---|---|---|
| `http_server_requests_seconds{quantile="0.95"}` | p95 response time | > 500ms |
| `http_server_requests_seconds_count{status="5.."}` | 5xx error count | > 10/min |
| `jvm_memory_used_bytes{area="heap"}` | JVM heap usage | > 80% of max |
| `hikaricp_connections_active` | Active DB connections | > 80% of max pool |
| `process_cpu_usage` | CPU usage | > 80% sustained |
| `nats_messages_published_total` | NATS messages published | Anomaly detection |

### Grafana Dashboards

Recommended dashboards:
1. **Service Overview** — health status, request rate, error rate, latency per service
2. **Database** — connection pool, query rate, slow queries, disk usage
3. **NATS** — message rate, consumer lag, stream sizes
4. **JVM** — heap, GC, threads per service
5. **Workflow** — task state distribution, transition rate, agent invocation rate

### Alert Rules (Prometheus)

```yaml
groups:
  - name: squadron-alerts
    rules:
      - alert: ServiceDown
        expr: up{job=~"squadron-.*"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Squadron service {{ $labels.job }} is down"

      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High 5xx error rate on {{ $labels.job }}"

      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "p95 latency > 1s on {{ $labels.job }}"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "DB connection pool > 90% on {{ $labels.job }}"
```

## Disaster Recovery

### Recovery Point Objective (RPO)

- **Database**: Last backup (automated daily) + WAL archiving (continuous)
- **NATS JetStream**: Last stream backup (if configured)
- **Redis**: Last RDB snapshot (if persistence enabled)
- **Configuration**: Stored in Git (version controlled)

### Recovery Time Objective (RTO)

| Component | Expected RTO | Procedure |
|---|---|---|
| Single service failure | < 2 minutes | HPA/restart, Pod auto-healing |
| Infrastructure failure | 5-15 minutes | Restore from backup, restart |
| Full cluster failure | 30-60 minutes | Restore infra + services from Helm |
| Data corruption | 1-4 hours | Point-in-time recovery from WAL |

### Full Cluster Recovery Procedure

1. **Restore infrastructure**:
   ```bash
   # Deploy infrastructure via Helm
   helm install postgresql bitnami/postgresql -n squadron-infra -f values-infra.yaml
   helm install redis bitnami/redis -n squadron-infra -f values-infra.yaml
   helm install nats nats/nats -n squadron-infra -f values-infra.yaml
   ```

2. **Restore databases**:
   ```bash
   for db in squadron_identity squadron_config squadron_orchestrator \
     squadron_platform squadron_agent squadron_workspace squadron_git \
     squadron_review squadron_notification; do
     createdb -h $PG_HOST -U squadron "$db"
     pg_restore -h $PG_HOST -U squadron -d "$db" "/backups/${db}-latest.dump"
   done
   ```

3. **Deploy services**:
   ```bash
   helm install squadron ./deploy/helm -n squadron-system -f values-production.yaml
   ```

4. **Verify**:
   ```bash
   # Check all pods are running
   kubectl -n squadron-system get pods
   # Check health endpoints
   # Run smoke tests
   cd deploy/loadtest && ./run-loadtest.sh --smoke
   ```

5. **Verify data integrity**:
   - Check task counts match expected
   - Verify recent state transitions are present
   - Confirm NATS streams are recreated (consumers will resubscribe)
