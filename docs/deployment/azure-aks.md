# Deploying Squadron on Azure Kubernetes Service (AKS)

This guide covers provisioning Azure infrastructure, configuring managed services, and
deploying the full Squadron platform on AKS for production use.

Squadron consists of 10 backend Spring Boot microservices, 1 Angular frontend, and
supporting infrastructure (PostgreSQL, Redis, NATS, Keycloak). This guide uses Azure
managed services where available and in-cluster components where no managed equivalent
exists.

## Prerequisites

| Requirement | Minimum Version | Purpose |
|---|---|---|
| Azure subscription | Pay-as-you-go or EA | Resource provisioning |
| Azure CLI (`az`) | 2.60+ | Infrastructure automation |
| `kubectl` | 1.31+ | Kubernetes management |
| Helm | 3.14+ | Chart deployment |
| Docker | 27+ | Image building |
| Java | 21 LTS | Building backend services |
| Maven | 3.9.x | Building backend services |
| Node.js | 22.x | Building Angular frontend |
| A registered domain | -- | DNS and TLS |

Verify your tools:

```bash
az version
kubectl version --client
helm version
docker --version
java -version
mvn -version
node --version
```

Login and set your subscription:

```bash
az login
az account set --subscription "<SUBSCRIPTION_ID>"
```

## 1. Azure Resource Provisioning

### 1.1 Variables

Set these once and reuse throughout the guide:

```bash
export LOCATION="eastus"
export RESOURCE_GROUP="rg-squadron-prod"
export AKS_CLUSTER="aks-squadron-prod"
export ACR_NAME="acrsquadronprod"          # must be globally unique, alphanumeric only
export PG_SERVER="psql-squadron-prod"
export REDIS_NAME="redis-squadron-prod"
export KEYVAULT_NAME="kv-squadron-prod"
export STORAGE_ACCOUNT="stsquadronprod"    # must be globally unique, alphanumeric only
export DOMAIN="squadron.example.com"
export VNET_NAME="vnet-squadron"
export SUBNET_AKS="subnet-aks"
export SUBNET_PG="subnet-pg"
export SUBNET_REDIS="subnet-redis"
```

### 1.2 Resource Group

```bash
az group create --name $RESOURCE_GROUP --location $LOCATION
```

### 1.3 Virtual Network

Create a VNet with dedicated subnets for AKS, PostgreSQL, and Redis:

```bash
az network vnet create \
  --resource-group $RESOURCE_GROUP \
  --name $VNET_NAME \
  --address-prefix 10.0.0.0/16 \
  --subnet-name $SUBNET_AKS \
  --subnet-prefix 10.0.0.0/20

az network vnet subnet create \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_PG \
  --address-prefix 10.0.16.0/24 \
  --delegations "Microsoft.DBforPostgreSQL/flexibleServers"

az network vnet subnet create \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_REDIS \
  --address-prefix 10.0.17.0/24

AKS_SUBNET_ID=$(az network vnet subnet show \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_AKS \
  --query id -o tsv)
```

### 1.4 AKS Cluster

Create a cluster with a system node pool and a user node pool sized for Squadron's
production resource requirements (~32 cores, 64 GB RAM minimum):

```bash
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --location $LOCATION \
  --kubernetes-version 1.31 \
  --node-count 3 \
  --node-vm-size Standard_D4s_v3 \
  --nodepool-name system \
  --vnet-subnet-id $AKS_SUBNET_ID \
  --network-plugin azure \
  --network-policy azure \
  --service-cidr 10.1.0.0/16 \
  --dns-service-ip 10.1.0.10 \
  --enable-managed-identity \
  --enable-oidc-issuer \
  --enable-workload-identity \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 6 \
  --generate-ssh-keys \
  --tier standard
```

Add a dedicated user node pool for Squadron workloads:

```bash
az aks nodepool add \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name squadron \
  --node-count 4 \
  --node-vm-size Standard_D4s_v3 \
  --vnet-subnet-id $AKS_SUBNET_ID \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 8 \
  --labels workload=squadron \
  --node-taints workload=squadron:PreferNoSchedule
```

Optional: add a GPU node pool for self-hosted AI models via Ollama:

```bash
az aks nodepool add \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name gpu \
  --node-count 1 \
  --node-vm-size Standard_NC6s_v3 \
  --enable-cluster-autoscaler \
  --min-count 0 \
  --max-count 2 \
  --labels workload=gpu \
  --node-taints nvidia.com/gpu=present:NoSchedule
```

Get cluster credentials:

```bash
az aks get-credentials --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER
kubectl get nodes
```

### 1.5 Azure Container Registry

```bash
az acr create \
  --resource-group $RESOURCE_GROUP \
  --name $ACR_NAME \
  --sku Premium \
  --admin-enabled false

# Attach ACR to AKS so the cluster can pull images without imagePullSecrets
az aks update \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --attach-acr $ACR_NAME
```

### 1.6 Azure Database for PostgreSQL Flexible Server

```bash
PG_SUBNET_ID=$(az network vnet subnet show \
  --resource-group $RESOURCE_GROUP \
  --vnet-name $VNET_NAME \
  --name $SUBNET_PG \
  --query id -o tsv)

az postgres flexible-server create \
  --resource-group $RESOURCE_GROUP \
  --name $PG_SERVER \
  --location $LOCATION \
  --version 17 \
  --sku-name Standard_D4s_v3 \
  --storage-size 256 \
  --admin-user squadronadmin \
  --admin-password '<STRONG_PASSWORD>' \
  --subnet $PG_SUBNET_ID \
  --private-dns-zone "privatelink.postgres.database.azure.com" \
  --tier GeneralPurpose \
  --high-availability ZoneRedundant \
  --backup-retention 35
```

Configure server parameters for Squadron workloads:

```bash
az postgres flexible-server parameter set \
  --resource-group $RESOURCE_GROUP \
  --server-name $PG_SERVER \
  --name max_connections --value 500

az postgres flexible-server parameter set \
  --resource-group $RESOURCE_GROUP \
  --server-name $PG_SERVER \
  --name shared_buffers --value 2097152

az postgres flexible-server parameter set \
  --resource-group $RESOURCE_GROUP \
  --server-name $PG_SERVER \
  --name azure.extensions --value "PGCRYPTO,UUID-OSSP"
```

### 1.7 Azure Cache for Redis

```bash
az redis create \
  --resource-group $RESOURCE_GROUP \
  --name $REDIS_NAME \
  --location $LOCATION \
  --sku Premium \
  --vm-size P1 \
  --enable-non-ssl-port false \
  --minimum-tls-version 1.2 \
  --replicas-per-master 1

# Get connection details
REDIS_HOST=$(az redis show --resource-group $RESOURCE_GROUP --name $REDIS_NAME \
  --query hostName -o tsv)
REDIS_KEY=$(az redis list-keys --resource-group $RESOURCE_GROUP --name $REDIS_NAME \
  --query primaryKey -o tsv)
```

### 1.8 Azure Key Vault

```bash
az keyvault create \
  --resource-group $RESOURCE_GROUP \
  --name $KEYVAULT_NAME \
  --location $LOCATION \
  --enable-rbac-authorization true \
  --sku premium

# Grant AKS managed identity access
AKS_IDENTITY=$(az aks show --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER \
  --query identityProfile.kubeletidentity.objectId -o tsv)

az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee $AKS_IDENTITY \
  --scope $(az keyvault show --name $KEYVAULT_NAME --query id -o tsv)
```

Store secrets:

```bash
az keyvault secret set --vault-name $KEYVAULT_NAME --name "pg-admin-password" \
  --value '<STRONG_PASSWORD>'
az keyvault secret set --vault-name $KEYVAULT_NAME --name "redis-primary-key" \
  --value "$REDIS_KEY"
az keyvault secret set --vault-name $KEYVAULT_NAME --name "keycloak-admin-password" \
  --value '<KEYCLOAK_PASSWORD>'
az keyvault secret set --vault-name $KEYVAULT_NAME --name "openai-api-key" \
  --value '<OPENAI_API_KEY>'
```

### 1.9 Storage Account

For NATS JetStream persistence and Keycloak data:

```bash
az storage account create \
  --resource-group $RESOURCE_GROUP \
  --name $STORAGE_ACCOUNT \
  --location $LOCATION \
  --sku Standard_ZRS \
  --kind StorageV2 \
  --min-tls-version TLS1_2
```

## 2. Building and Pushing Images

### 2.1 Build Backend Services

```bash
# From the repository root
mvn clean package -DskipTests

# Build all Docker images
services=(gateway identity config orchestrator platform agent workspace git review notification)
for svc in "${services[@]}"; do
  docker build -t ${ACR_NAME}.azurecr.io/squadron/squadron-${svc}:0.1.0 \
    -f squadron-${svc}/Dockerfile squadron-${svc}/
done
```

### 2.2 Build Frontend

```bash
cd squadron-ui
npm ci
npx ng build --configuration=production
docker build -t ${ACR_NAME}.azurecr.io/squadron/squadron-ui:0.1.0 .
cd ..
```

### 2.3 Push to ACR

```bash
az acr login --name $ACR_NAME

for svc in gateway identity config orchestrator platform agent workspace git review notification ui; do
  docker push ${ACR_NAME}.azurecr.io/squadron/squadron-${svc}:0.1.0
done
```

Alternatively, use ACR Tasks to build in the cloud without a local Docker daemon:

```bash
for svc in gateway identity config orchestrator platform agent workspace git review notification; do
  az acr build \
    --registry $ACR_NAME \
    --image squadron/squadron-${svc}:0.1.0 \
    --file squadron-${svc}/Dockerfile \
    squadron-${svc}/
done

az acr build \
  --registry $ACR_NAME \
  --image squadron/squadron-ui:0.1.0 \
  --file squadron-ui/Dockerfile \
  squadron-ui/
```

## 3. Configuring Azure Managed Services

### 3.1 PostgreSQL Databases

Squadron requires 9 service databases plus 1 for Keycloak. Connect and create them:

```bash
PG_FQDN="${PG_SERVER}.postgres.database.azure.com"

# Install the psql client if not available
# az postgres flexible-server execute can also be used

psql "host=${PG_FQDN} port=5432 dbname=postgres user=squadronadmin password=<PASSWORD> sslmode=require" <<'SQL'
CREATE DATABASE squadron_identity;
CREATE DATABASE squadron_config;
CREATE DATABASE squadron_orchestrator;
CREATE DATABASE squadron_platform;
CREATE DATABASE squadron_agent;
CREATE DATABASE squadron_workspace;
CREATE DATABASE squadron_git;
CREATE DATABASE squadron_review;
CREATE DATABASE squadron_notification;
CREATE DATABASE keycloak;

-- Enable pgcrypto on each database
\c squadron_identity
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_config
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_orchestrator
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_platform
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_agent
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_workspace
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_git
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_review
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c squadron_notification
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
\c keycloak
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
SQL
```

### 3.2 Azure Key Vault CSI Driver

Install the Secrets Store CSI Driver to mount Key Vault secrets into pods:

```bash
az aks enable-addons \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --addons azure-keyvault-secrets-provider

# Verify the driver is running
kubectl get pods -n kube-system -l app=secrets-store-csi-driver
```

Create a `SecretProviderClass` for Squadron:

```yaml
# deploy/azure/secret-provider-class.yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: squadron-azure-keyvault
  namespace: squadron
spec:
  provider: azure
  parameters:
    usePodIdentity: "false"
    useVMManagedIdentity: "true"
    userAssignedIdentityID: ""  # uses kubelet identity
    keyvaultName: "<KEYVAULT_NAME>"
    tenantId: "<AZURE_TENANT_ID>"
    objects: |
      array:
        - |
          objectName: pg-admin-password
          objectType: secret
        - |
          objectName: redis-primary-key
          objectType: secret
        - |
          objectName: keycloak-admin-password
          objectType: secret
        - |
          objectName: openai-api-key
          objectType: secret
  secretObjects:
    - secretName: squadron-db-credentials
      type: Opaque
      data:
        - objectName: pg-admin-password
          key: password
    - secretName: squadron-redis-credentials
      type: Opaque
      data:
        - objectName: redis-primary-key
          key: password
    - secretName: squadron-keycloak-credentials
      type: Opaque
      data:
        - objectName: keycloak-admin-password
          key: password
    - secretName: squadron-ai-credentials
      type: Opaque
      data:
        - objectName: openai-api-key
          key: api-key
```

```bash
kubectl create namespace squadron
kubectl apply -f deploy/azure/secret-provider-class.yaml
```

### 3.3 Azure OpenAI Service

To use Azure OpenAI instead of the public OpenAI API, create a deployment:

```bash
# Create Azure OpenAI resource
az cognitiveservices account create \
  --resource-group $RESOURCE_GROUP \
  --name "aoai-squadron-prod" \
  --kind OpenAI \
  --sku S0 \
  --location $LOCATION

# Deploy a model
az cognitiveservices account deployment create \
  --resource-group $RESOURCE_GROUP \
  --name "aoai-squadron-prod" \
  --deployment-name "gpt-4o" \
  --model-name "gpt-4o" \
  --model-version "2024-08-06" \
  --model-format OpenAI \
  --sku-capacity 80 \
  --sku-name Standard

# Get the endpoint and key
AOAI_ENDPOINT=$(az cognitiveservices account show \
  --resource-group $RESOURCE_GROUP --name "aoai-squadron-prod" \
  --query properties.endpoint -o tsv)
AOAI_KEY=$(az cognitiveservices account keys list \
  --resource-group $RESOURCE_GROUP --name "aoai-squadron-prod" \
  --query key1 -o tsv)

# Store in Key Vault
az keyvault secret set --vault-name $KEYVAULT_NAME --name "aoai-api-key" --value "$AOAI_KEY"
```

Configure Squadron's agent service to use the Azure endpoint by setting:

```
OPENAI_BASE_URL=<AOAI_ENDPOINT>/openai/deployments/gpt-4o
OPENAI_API_KEY=<AOAI_KEY>
OPENAI_MODEL=gpt-4o
```

## 4. Deploying In-Cluster Infrastructure

NATS has no Azure managed equivalent. Keycloak can optionally be replaced by Azure AD
B2C, but most deployments run it in-cluster for full control.

### 4.1 NATS with JetStream

```bash
helm repo add nats https://nats-io.github.io/k8s/helm/charts/
helm repo update

helm install nats nats/nats \
  --namespace squadron \
  --set config.jetstream.enabled=true \
  --set config.jetstream.fileStore.pvc.size=50Gi \
  --set config.jetstream.fileStore.pvc.storageClassName=managed-csi \
  --set cluster.enabled=true \
  --set cluster.replicas=3 \
  --set resources.requests.cpu=500m \
  --set resources.requests.memory=512Mi \
  --set resources.limits.cpu=1 \
  --set resources.limits.memory=1Gi
```

### 4.2 Keycloak

Deploy Keycloak backed by Azure Database for PostgreSQL:

```bash
helm repo add codecentric https://codecentric.github.io/helm-charts
helm repo update

PG_FQDN="${PG_SERVER}.postgres.database.azure.com"

helm install keycloak codecentric/keycloakx \
  --namespace squadron \
  --set database.vendor=postgres \
  --set database.hostname=$PG_FQDN \
  --set database.port=5432 \
  --set database.database=keycloak \
  --set database.username=squadronadmin \
  --set database.existingSecret=squadron-db-credentials \
  --set database.existingSecretKey=password \
  --set resources.requests.cpu=1 \
  --set resources.requests.memory=1Gi \
  --set resources.limits.cpu=2 \
  --set resources.limits.memory=2Gi \
  --set replicas=2
```

## 5. Deploying Squadron

### 5.1 Azure Values Override

Create a `values-azure.yaml` that adapts the Helm chart for Azure managed services:

```yaml
# deploy/helm/squadron/values-azure.yaml
global:
  namespace: squadron
  imageRegistry: "<ACR_NAME>.azurecr.io"
  storageClass: managed-csi

gateway:
  replicas: 3
  image:
    repository: squadron/squadron-gateway
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SSL_ENABLED: "true"
    SPRING_DATA_REDIS_HOST: "<REDIS_NAME>.redis.cache.windows.net"
    SPRING_DATA_REDIS_PORT: "6380"
    SPRING_DATA_REDIS_SSL_ENABLED: "true"

identity:
  replicas: 2
  image:
    repository: squadron/squadron-identity
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_identity?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

config:
  replicas: 2
  image:
    repository: squadron/squadron-config
    tag: "0.1.0"
  resources:
    requests: { cpu: 250m, memory: 512Mi }
    limits:   { cpu: 500m, memory: 1Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_config?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

orchestrator:
  replicas: 3
  image:
    repository: squadron/squadron-orchestrator
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_orchestrator?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

platform:
  replicas: 2
  image:
    repository: squadron/squadron-platform
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_platform?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

agent:
  replicas: 3
  image:
    repository: squadron/squadron-agent
    tag: "0.1.0"
  resources:
    requests: { cpu: "1", memory: 2Gi }
    limits:   { cpu: "2", memory: 4Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_agent?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"
    OPENAI_BASE_URL: "<AOAI_ENDPOINT>/openai/deployments/gpt-4o"
    OPENAI_MODEL: "gpt-4o"

workspace:
  replicas: 2
  image:
    repository: squadron/squadron-workspace
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_workspace?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

git:
  replicas: 2
  image:
    repository: squadron/squadron-git
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_git?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

review:
  replicas: 2
  image:
    repository: squadron/squadron-review
    tag: "0.1.0"
  resources:
    requests: { cpu: 500m, memory: 1Gi }
    limits:   { cpu: "1", memory: 2Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_review?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

notification:
  replicas: 2
  image:
    repository: squadron/squadron-notification
    tag: "0.1.0"
  resources:
    requests: { cpu: 250m, memory: 512Mi }
    limits:   { cpu: 500m, memory: 1Gi }
  env:
    SPRING_DATASOURCE_URL: "jdbc:postgresql://<PG_FQDN>:5432/squadron_notification?sslmode=require"
    SPRING_DATASOURCE_USERNAME: "squadronadmin"

# Disable in-cluster PostgreSQL and Redis (using Azure managed services)
postgresql:
  enabled: false

redis:
  enabled: false

# Keep in-cluster NATS and Keycloak
nats:
  enabled: false   # deployed separately via nats/nats chart

keycloak:
  enabled: false   # deployed separately via codecentric chart

monitoring:
  enabled: true
  serviceMonitor:
    interval: 30s
    path: /actuator/prometheus
```

### 5.2 Helm Install

```bash
helm install squadron deploy/helm/squadron \
  --namespace squadron \
  --values deploy/helm/squadron/values-prod.yaml \
  --values deploy/helm/squadron/values-azure.yaml \
  --set-string gateway.env.SPRING_DATA_REDIS_PASSWORD="$REDIS_KEY" \
  --set-string identity.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string config.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string orchestrator.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string platform.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string agent.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string agent.env.OPENAI_API_KEY='<AOAI_KEY>' \
  --set-string workspace.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string git.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string review.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --set-string notification.env.SPRING_DATASOURCE_PASSWORD='<PG_PASSWORD>' \
  --wait --timeout 10m
```

> For secrets management in production, reference the Key Vault CSI
> `SecretProviderClass` (section 3.2) rather than passing passwords via `--set`.

### 5.3 Verify Deployment

```bash
kubectl -n squadron get pods
kubectl -n squadron get svc

# Check all deployments are healthy
kubectl -n squadron rollout status deployment/squadron-gateway
kubectl -n squadron rollout status deployment/squadron-orchestrator
kubectl -n squadron rollout status deployment/squadron-agent

# Test health endpoints
kubectl -n squadron port-forward svc/squadron-gateway 8443:8443 &
curl -k https://localhost:8443/actuator/health
```

## 6. Ingress and TLS

### 6.1 NGINX Ingress Controller

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-health-probe-request-path"=/healthz \
  --set controller.service.externalTrafficPolicy=Local \
  --set controller.replicaCount=2
```

### 6.2 Alternative: Azure Application Gateway Ingress Controller (AGIC)

```bash
az aks enable-addons \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --addons ingress-appgw \
  --appgw-name "agw-squadron" \
  --appgw-subnet-cidr "10.0.20.0/24"
```

### 6.3 cert-manager with Let's Encrypt

```bash
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --set crds.enabled=true
```

Create a `ClusterIssuer`:

```yaml
# deploy/azure/cluster-issuer.yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: platform-team@example.com
    privateKeySecretRef:
      name: letsencrypt-prod-key
    solvers:
      - http01:
          ingress:
            class: nginx
```

```bash
kubectl apply -f deploy/azure/cluster-issuer.yaml
```

### 6.4 Ingress Resource

```yaml
# deploy/azure/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: squadron-ingress
  namespace: squadron
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    # WebSocket support for STOMP
    nginx.ingress.kubernetes.io/proxy-http-version: "1.1"
    nginx.ingress.kubernetes.io/configuration-snippet: |
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - squadron.example.com
      secretName: squadron-tls
  rules:
    - host: squadron.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: squadron-gateway
                port:
                  number: 8443
```

```bash
kubectl apply -f deploy/azure/ingress.yaml
```

### 6.5 DNS Configuration

Point your domain to the ingress controller's external IP:

```bash
INGRESS_IP=$(kubectl -n ingress-nginx get svc ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')

az network dns record-set a add-record \
  --resource-group $RESOURCE_GROUP \
  --zone-name example.com \
  --record-set-name squadron \
  --ipv4-address $INGRESS_IP
```

## 7. Azure AD Integration

Configure Keycloak to federate with Azure AD for enterprise SSO.

### 7.1 Register an App in Azure AD

```bash
APP_ID=$(az ad app create \
  --display-name "Squadron Platform" \
  --sign-in-audience AzureADMyOrg \
  --web-redirect-uris "https://${DOMAIN}/auth/realms/squadron/broker/azure-ad/endpoint" \
  --query appId -o tsv)

az ad app credential reset --id $APP_ID --query password -o tsv
# Save this client secret securely

az keyvault secret set --vault-name $KEYVAULT_NAME \
  --name "azure-ad-client-secret" --value '<CLIENT_SECRET>'
```

### 7.2 Configure Keycloak Identity Provider

In the Keycloak admin console (`https://<DOMAIN>/auth`):

1. Navigate to **Realm Settings > Identity Providers > Add Provider > OpenID Connect v1.0**
2. Set:
   - **Alias**: `azure-ad`
   - **Authorization URL**: `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/authorize`
   - **Token URL**: `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/token`
   - **Client ID**: `<APP_ID>` from step 7.1
   - **Client Secret**: the credential from step 7.1
   - **Default Scopes**: `openid profile email`
3. Under **Mappers**, create mappers for `preferred_username`, `email`, and `name` claims.

### 7.3 Verify SSO

```bash
# Open the Squadron UI; the login page should show an "Azure AD" button
open "https://${DOMAIN}"
```

## 8. Monitoring

### 8.1 Azure Monitor and Container Insights

```bash
az aks enable-addons \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --addons monitoring \
  --workspace-resource-id $(az monitor log-analytics workspace create \
    --resource-group $RESOURCE_GROUP \
    --workspace-name "law-squadron-prod" \
    --query id -o tsv)
```

### 8.2 Prometheus and Grafana

Use Azure Managed Prometheus and Grafana:

```bash
# Create Azure Monitor workspace for Prometheus
az monitor account create \
  --resource-group $RESOURCE_GROUP \
  --name "amp-squadron-prod" \
  --location $LOCATION

# Create Azure Managed Grafana
az grafana create \
  --resource-group $RESOURCE_GROUP \
  --name "grafana-squadron-prod" \
  --location $LOCATION

# Link Prometheus to AKS
PROMETHEUS_ID=$(az monitor account show \
  --resource-group $RESOURCE_GROUP --name "amp-squadron-prod" --query id -o tsv)

az aks update \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --enable-azure-monitor-metrics \
  --azure-monitor-workspace-resource-id $PROMETHEUS_ID

# Link Grafana to Prometheus
GRAFANA_ID=$(az grafana show \
  --resource-group $RESOURCE_GROUP --name "grafana-squadron-prod" --query id -o tsv)

az monitor account link-grafana \
  --resource-group $RESOURCE_GROUP \
  --name "amp-squadron-prod" \
  --grafana-resource-id $GRAFANA_ID
```

Squadron services expose metrics at `/actuator/prometheus`. The `ServiceMonitor` in the
Helm chart configures scraping at 30-second intervals. Azure Managed Prometheus
automatically discovers `ServiceMonitor` resources when the monitoring addon is enabled.

### 8.3 Key Metrics to Monitor

| Metric | Source | Alert Threshold |
|---|---|---|
| `http_server_requests_seconds` | Gateway, all services | p99 > 5s |
| `jvm_memory_used_bytes` | All services | > 85% of limit |
| `spring_ai_chat_completions_seconds` | Agent | p95 > 30s |
| `db_pool_active_connections` | All DB services | > 80% of pool |
| `nats_messages_received_total` | NATS exporter | Drop > 50% |

## 9. Scaling

### 9.1 Cluster Autoscaler

Already enabled during cluster creation. Adjust limits:

```bash
az aks nodepool update \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name squadron \
  --min-count 3 \
  --max-count 12 \
  --update-cluster-autoscaler
```

### 9.2 Horizontal Pod Autoscaler

Create HPAs for critical services:

```yaml
# deploy/azure/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: squadron-gateway-hpa
  namespace: squadron
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: squadron-gateway
  minReplicas: 3
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
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: squadron-agent-hpa
  namespace: squadron
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: squadron-agent
  minReplicas: 3
  maxReplicas: 8
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 75
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: squadron-orchestrator-hpa
  namespace: squadron
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: squadron-orchestrator
  minReplicas: 3
  maxReplicas: 8
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

```bash
kubectl apply -f deploy/azure/hpa.yaml
```

### 9.3 KEDA for Event-Driven Scaling (Optional)

Scale the agent service based on NATS queue depth:

```bash
helm repo add kedacore https://kedacore.github.io/charts
helm install keda kedacore/keda --namespace keda --create-namespace
```

## 10. Cost Optimization

### 10.1 Spot Instances

Use spot VMs for non-critical workloads (notification, config):

```bash
az aks nodepool add \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name spot \
  --node-count 2 \
  --node-vm-size Standard_D4s_v3 \
  --priority Spot \
  --spot-max-price -1 \
  --eviction-policy Delete \
  --labels workload=spot \
  --node-taints "kubernetes.azure.com/scalesetpriority=spot:NoSchedule"
```

Add tolerations to non-critical services in their deployment specs:

```yaml
notification:
  env: {}
  tolerations:
    - key: "kubernetes.azure.com/scalesetpriority"
      operator: "Equal"
      value: "spot"
      effect: "NoSchedule"
  nodeSelector:
    workload: spot
```

### 10.2 Reserved Instances

For steady-state workloads, purchase 1-year or 3-year reserved instances for the
`Standard_D4s_v3` VMs in the system and squadron node pools. This provides 30-60%
savings over pay-as-you-go pricing.

### 10.3 Right-Sizing

After 2 weeks of production traffic, review actual usage:

```bash
# Check resource consumption
kubectl top pods -n squadron --sort-by=cpu
kubectl top pods -n squadron --sort-by=memory

# Review HPA status
kubectl get hpa -n squadron
```

Adjust `requests` and `limits` in `values-azure.yaml` based on observed usage. Over-
provisioning wastes cost; under-provisioning causes OOM kills and throttling.

### 10.4 Scale Down During Off-Hours

For non-24/7 environments:

```bash
# Scale down user node pool at night
az aks nodepool update \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name squadron \
  --min-count 1 \
  --max-count 4 \
  --update-cluster-autoscaler
```

## 11. Security

### 11.1 Azure RBAC for AKS

Enable Azure RBAC for Kubernetes authorization:

```bash
az aks update \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --enable-azure-rbac \
  --enable-aad

# Grant cluster admin to your team
az role assignment create \
  --role "Azure Kubernetes Service Cluster Admin Role" \
  --assignee "<TEAM_GROUP_OBJECT_ID>" \
  --scope $(az aks show --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER --query id -o tsv)
```

### 11.2 Workload Identity

Use Azure Workload Identity instead of storing credentials in Kubernetes secrets:

```bash
# Create a user-assigned managed identity
az identity create \
  --resource-group $RESOURCE_GROUP \
  --name "id-squadron-workload"

IDENTITY_CLIENT_ID=$(az identity show --resource-group $RESOURCE_GROUP \
  --name "id-squadron-workload" --query clientId -o tsv)

# Create federated credential
AKS_OIDC_ISSUER=$(az aks show --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER --query oidcIssuerProfile.issuerUrl -o tsv)

az identity federated-credential create \
  --name "fc-squadron" \
  --identity-name "id-squadron-workload" \
  --resource-group $RESOURCE_GROUP \
  --issuer $AKS_OIDC_ISSUER \
  --subject "system:serviceaccount:squadron:squadron-sa" \
  --audiences "api://AzureADTokenExchange"

# Grant the identity access to Key Vault
az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee $IDENTITY_CLIENT_ID \
  --scope $(az keyvault show --name $KEYVAULT_NAME --query id -o tsv)

# Grant access to Azure Database for PostgreSQL
az role assignment create \
  --role "Contributor" \
  --assignee $IDENTITY_CLIENT_ID \
  --scope $(az postgres flexible-server show \
    --resource-group $RESOURCE_GROUP --name $PG_SERVER --query id -o tsv)
```

Create the service account in Kubernetes:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: squadron-sa
  namespace: squadron
  annotations:
    azure.workload.identity/client-id: "<IDENTITY_CLIENT_ID>"
  labels:
    azure.workload.identity/use: "true"
```

### 11.3 Network Policies

The Helm chart includes default-deny ingress policies with explicit allow rules for
inter-service communication. Since the AKS cluster was created with `--network-policy
azure`, these policies are enforced by the Azure CNI network policy engine.

Key policies deployed by the chart:

- **Default deny** all ingress to Squadron pods
- **Gateway -> all backends** on their respective ports
- **All backends -> PostgreSQL** on port 5432
- **All backends -> NATS** on ports 4222, 8222
- **Gateway -> Redis** on port 6379
- **Agent -> orchestrator, git, review, workspace** (Feign clients)
- **Orchestrator -> platform** (Feign client)
- **Identity <-> Keycloak** on port 8080
- **External -> gateway** on port 8443

### 11.4 Private AKS Cluster

For maximum isolation, convert to a private cluster:

```bash
az aks update \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --enable-private-cluster \
  --private-dns-zone system

# Access via a jump box or VPN gateway in the same VNet
```

## 12. Backup and Disaster Recovery

### 12.1 Azure PostgreSQL Backup

Azure Database for PostgreSQL Flexible Server includes automatic backups with 35-day
retention (configured during creation). Enable geo-redundant backup:

```bash
az postgres flexible-server update \
  --resource-group $RESOURCE_GROUP \
  --name $PG_SERVER \
  --geo-redundant-backup Enabled
```

Point-in-time restore:

```bash
az postgres flexible-server restore \
  --resource-group $RESOURCE_GROUP \
  --name "${PG_SERVER}-restored" \
  --source-server $PG_SERVER \
  --restore-time "2025-01-15T10:30:00Z"
```

### 12.2 AKS Backup

Use Azure Backup for AKS to protect cluster state and persistent volumes:

```bash
az dataprotection backup-vault create \
  --resource-group $RESOURCE_GROUP \
  --vault-name "bv-squadron-prod" \
  --type SystemAssigned \
  --storage-settings datastore-type=VaultStore type=GeoRedundant \
  --location $LOCATION

# Install the AKS backup extension
az aks trustedaccess rolebinding create \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name squadron-backup \
  --source-resource-id $(az dataprotection backup-vault show \
    --resource-group $RESOURCE_GROUP --vault-name "bv-squadron-prod" --query id -o tsv) \
  --roles "Microsoft.DataProtection/backupVaults/backup-operator"
```

### 12.3 NATS JetStream Recovery

NATS JetStream data is stored on Azure managed disks via PVCs. Back up the PVCs using
the AKS backup extension or Azure Disk snapshots:

```bash
# Snapshot the NATS data disk
PV_NAME=$(kubectl -n squadron get pvc nats-js-pvc -o jsonpath='{.spec.volumeName}')
DISK_ID=$(kubectl get pv $PV_NAME -o jsonpath='{.spec.csi.volumeHandle}')

az snapshot create \
  --resource-group "MC_${RESOURCE_GROUP}_${AKS_CLUSTER}_${LOCATION}" \
  --name "snap-nats-$(date +%Y%m%d)" \
  --source "$DISK_ID"
```

## 13. Upgrading

### 13.1 AKS Version Upgrades

```bash
# Check available versions
az aks get-upgrades \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --output table

# Upgrade control plane first
az aks upgrade \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --kubernetes-version 1.32 \
  --control-plane-only

# Upgrade node pools one at a time
az aks nodepool upgrade \
  --resource-group $RESOURCE_GROUP \
  --cluster-name $AKS_CLUSTER \
  --name squadron \
  --kubernetes-version 1.32
```

### 13.2 Squadron Helm Upgrades

```bash
# Build and push new version
export NEW_VERSION="0.2.0"
for svc in gateway identity config orchestrator platform agent workspace git review notification ui; do
  docker build -t ${ACR_NAME}.azurecr.io/squadron/squadron-${svc}:${NEW_VERSION} \
    squadron-${svc}/
  docker push ${ACR_NAME}.azurecr.io/squadron/squadron-${svc}:${NEW_VERSION}
done

# Upgrade the release
helm upgrade squadron deploy/helm/squadron \
  --namespace squadron \
  --values deploy/helm/squadron/values-prod.yaml \
  --values deploy/helm/squadron/values-azure.yaml \
  --set global.imageTag=${NEW_VERSION} \
  --wait --timeout 10m

# Verify rollout
for svc in gateway identity config orchestrator platform agent workspace git review notification; do
  kubectl -n squadron rollout status deployment/squadron-${svc}
done
```

### 13.3 Rollback

```bash
# List releases
helm history squadron -n squadron

# Rollback to a previous revision
helm rollback squadron <REVISION> -n squadron

# Verify
kubectl -n squadron get pods
```

### 13.4 Blue-Green with Azure Traffic Manager

For zero-downtime major upgrades, deploy a second AKS cluster and use Azure Traffic
Manager to shift traffic:

```bash
az network traffic-manager profile create \
  --resource-group $RESOURCE_GROUP \
  --name "tm-squadron" \
  --routing-method Weighted \
  --unique-dns-name "squadron-tm"

# Add blue (current) endpoint
az network traffic-manager endpoint create \
  --resource-group $RESOURCE_GROUP \
  --profile-name "tm-squadron" \
  --name "blue" \
  --type externalEndpoints \
  --target "blue.squadron.example.com" \
  --weight 100

# Add green (new) endpoint with weight 0, then gradually shift
az network traffic-manager endpoint create \
  --resource-group $RESOURCE_GROUP \
  --profile-name "tm-squadron" \
  --name "green" \
  --type externalEndpoints \
  --target "green.squadron.example.com" \
  --weight 0

# Shift 10% traffic to green
az network traffic-manager endpoint update \
  --resource-group $RESOURCE_GROUP \
  --profile-name "tm-squadron" \
  --name "green" \
  --weight 10
az network traffic-manager endpoint update \
  --resource-group $RESOURCE_GROUP \
  --profile-name "tm-squadron" \
  --name "blue" \
  --weight 90
```

## 14. Troubleshooting

### ACR Pull Errors

```bash
# Verify ACR attachment
az aks check-acr --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER --acr ${ACR_NAME}.azurecr.io

# Re-attach if needed
az aks update --resource-group $RESOURCE_GROUP --name $AKS_CLUSTER --attach-acr $ACR_NAME

# Check image pull events on a failing pod
kubectl -n squadron describe pod <POD_NAME> | grep -A5 Events
```

### AKS Networking Issues

```bash
# Verify DNS resolution from within a pod
kubectl -n squadron run -it --rm debug --image=busybox -- nslookup squadron-gateway

# Check if network policies are blocking traffic
kubectl -n squadron get networkpolicy
kubectl -n squadron describe networkpolicy squadron-default-deny

# Test connectivity between services
kubectl -n squadron exec -it deploy/squadron-gateway -- curl -s http://squadron-identity:8081/actuator/health
```

### Managed PostgreSQL Connectivity

```bash
# Test connectivity from within the cluster
kubectl -n squadron run -it --rm pg-test --image=postgres:17 -- \
  psql "host=${PG_SERVER}.postgres.database.azure.com port=5432 dbname=squadron_identity user=squadronadmin sslmode=require"

# Check VNet integration
az postgres flexible-server show \
  --resource-group $RESOURCE_GROUP --name $PG_SERVER \
  --query network -o json

# Verify private DNS zone is linked
az network private-dns zone list --resource-group $RESOURCE_GROUP -o table
```

### Managed Redis Connectivity

```bash
# Test from a pod
kubectl -n squadron run -it --rm redis-test --image=redis:7 -- \
  redis-cli -h ${REDIS_NAME}.redis.cache.windows.net -p 6380 --tls -a "$REDIS_KEY" PING

# Check firewall rules
az redis firewall-rules list --resource-group $RESOURCE_GROUP --name $REDIS_NAME -o table
```

### Service Health Checks

```bash
# Check all pod statuses
kubectl -n squadron get pods -o wide

# View logs for a failing service
kubectl -n squadron logs deploy/squadron-agent --tail=100

# Check Flyway migration status (in pod logs during startup)
kubectl -n squadron logs deploy/squadron-orchestrator | grep -i flyway

# Check NATS JetStream health
kubectl -n squadron port-forward svc/nats 8222:8222 &
curl http://localhost:8222/jsz
```

### Pod Stuck in Pending

```bash
# Check if nodes are available
kubectl get nodes
kubectl describe node <NODE_NAME> | grep -A10 "Allocated resources"

# Check if cluster autoscaler is working
kubectl -n kube-system logs deploy/cluster-autoscaler --tail=50

# Check PVC binding (for stateful services)
kubectl -n squadron get pvc
kubectl -n squadron describe pvc <PVC_NAME>
```

### Azure OpenAI Errors

```bash
# Check agent logs for AI errors
kubectl -n squadron logs deploy/squadron-agent --tail=200 | grep -i "openai\|error\|429\|rate"

# Verify Azure OpenAI endpoint is reachable
kubectl -n squadron exec -it deploy/squadron-agent -- \
  curl -s -H "api-key: <KEY>" "<AOAI_ENDPOINT>/openai/deployments/gpt-4o/chat/completions?api-version=2024-08-01-preview" \
  -d '{"messages":[{"role":"user","content":"ping"}]}'

# Check quota
az cognitiveservices usage list --location $LOCATION -o table
```
