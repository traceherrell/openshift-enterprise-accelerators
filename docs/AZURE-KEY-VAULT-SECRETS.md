# Unified Azure Key Vault Secrets Management Guide

This document establishes the enterprise procedure for managing application secrets on both **On-Premises OpenShift Container Platform (OCP)** and **Azure Red Hat OpenShift (ARO)** using **Azure Key Vault** as the single source of truth.

---

## 1. Secrets Management Architecture

```
                      ┌──────────────────────────────────────┐
                      │    Azure Key Vault (Cloud Service)   │
                      │       kv-enterprise-secrets          │
                      └──────────────────┬───────────────────┘
                                         │
             ┌───────────────────────────┴───────────────────────────┐
             │ HTTPS / Port 443 (Internet / ExpressRoute)            │
             ▼                                                       ▼
┌──────────────────────────────────────┐   ┌──────────────────────────────────────┐
│        On-Premises OpenShift         │   │   Azure Red Hat OpenShift (ARO)      │
│                                      │   │                                      │
│  [Azure Service Principal Secret]    │   │  [User-Assigned Managed Identity]    │
│                 │                    │   │                 │                    │
│                 ▼                    │   │                 ▼                    │
│     Secrets Store CSI Driver         │   │     Secrets Store CSI Driver         │
│                 │                    │   │                 │                    │
│                 ▼                    │   │                 ▼                    │
│   SecretProviderClass (Banking)      │   │   SecretProviderClass (Trading)      │
│                 │                    │   │                 │                    │
│                 ▼ (Syncs Secret)     │   │                 ▼ (Syncs Secret)     │
│   Native Secret: app-secrets         │   │   Native Secret: trade-secrets       │
│                 │                    │   │                 │                    │
│                 ▼ (envFrom)          │   │                 ▼ (envFrom)          │
│   Pods: payment & transaction        │   │   Pods: trade & portfolio            │
└──────────────────────────────────────┘   └──────────────────────────────────────┘
```

### Key Architectural Tenets
1. **Zero Plaintext Secrets in Source Code**: No passwords, private keys, or tokens are committed to Git.
2. **Unified Enterprise Secrets Engine**: Both on-premise and cloud clusters pull from Azure Key Vault, eliminating the operational overhead of running disparate local vault servers.
3. **Native Kubernetes Secret Synchronization**: The Secrets Store CSI Driver automatically synchronizes Azure Key Vault secrets into native Kubernetes Secret objects (`app-secrets` and `trade-secrets`).
4. **Zero Cloud SDK Coupling**: Application containers consume credentials as standard environment variables (`APP_SECURITY_USER`, `APP_SECURITY_PASSWORD`). No proprietary Azure SDKs are bundled in the microservices.

---

## 2. Secrets Provisioning in Azure Key Vault

### 2.1 Automated Provisioning Script
An automated setup script is provided in [`azure-keyvault/setup-azure-keyvault.sh`](../azure-keyvault/setup-azure-keyvault.sh):

```bash
# Run automated Azure Key Vault provisioning
cd azure-keyvault
./setup-azure-keyvault.sh
```

### 2.2 Manual Azure CLI Commands
If provisioning manually:

```bash
# Variables
RESOURCE_GROUP="rg-enterprise-platform"
KEYVAULT_NAME="kv-enterprise-secrets"
LOCATION="eastus"

# 1. Create Azure Key Vault with RBAC enabled
az keyvault create \
  --name "$KEYVAULT_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --enable-rbac-authorization true

# 2. Add Banking Platform Secrets (On-Premises)
az keyvault secret set --vault-name "$KEYVAULT_NAME" --name "payment-api-user" --value "api-user"
az keyvault secret set --vault-name "$KEYVAULT_NAME" --name "payment-api-password" --value "SuperSecurePass123!"
az keyvault secret set --vault-name "$KEYVAULT_NAME" --name "kafka-truststore-password" --value ""
az keyvault secret set --vault-name "$KEYVAULT_NAME" --name "logging-truststore-password" --value ""

# 3. Add Trading Platform Secrets (ARO)
az keyvault secret set --vault-name "$KEYVAULT_NAME" --name "trade-api-user" --value "api-user"
az keyvault secret set --vault-name "$KEYVAULT_NAME" --name "trade-api-password" --value "SuperSecurePass123!"
```

---

## 3. Cluster Authentication to Azure Key Vault

### 3.1 On-Premises OpenShift (Azure Service Principal)
On-Premises clusters communicate with Azure Key Vault over outbound HTTPS (port 443 / ExpressRoute):

1. **Create an Azure Service Principal**:
   ```bash
   az ad sp create-for-rbac --name "sp-ocp-onprem-keyvault" --skip-assignment
   ```
2. **Assign the `Key Vault Secrets User` Role**:
   ```bash
   KEYVAULT_ID=$(az keyvault show --name "$KEYVAULT_NAME" --query id -o tsv)
   az role assignment create \
     --role "Key Vault Secrets User" \
     --assignee "<SP_CLIENT_ID>" \
     --scope "$KEYVAULT_ID"
   ```
3. **Store Credentials on OpenShift**:
   ```bash
   oc create secret generic azure-sp-credentials \
     --from-literal=clientid="<SP_CLIENT_ID>" \
     --from-literal=clientsecret="<SP_CLIENT_SECRET>" \
     -n payment-app
   ```
4. **Reference in `SecretProviderClass`** ([`On-Prem-Microservice-1/deployment/02-secret-provider-class.yaml`](../On-Prem-Microservice-1/deployment/02-secret-provider-class.yaml)).

### 3.2 Azure Red Hat OpenShift (Managed Identity / Workload Identity)
On ARO, Azure-native User-Assigned Managed Identity (UAMI) eliminates static credentials:

1. **Create Managed Identity**:
   ```bash
   az identity create --name "uami-aro-secrets" --resource-group "$RESOURCE_GROUP"
   UAMI_CLIENT_ID=$(az identity show --name "uami-aro-secrets" --resource-group "$RESOURCE_GROUP" --query clientId -o tsv)
   ```
2. **Assign `Key Vault Secrets User` Role**:
   ```bash
   az role assignment create \
     --role "Key Vault Secrets User" \
     --assignee "$UAMI_CLIENT_ID" \
     --scope "$KEYVAULT_ID"
   ```
3. **Reference in `SecretProviderClass`** ([`ARO-Microservice-1/deployment/02-secret-provider-class.yaml`](../ARO-Microservice-1/deployment/02-secret-provider-class.yaml)).

---

## 4. Secret Synchronization & In-Pod Consumption

### 4.1 Declarative `SecretProviderClass`
Both environments use `secretObjects` to project Azure Key Vault values into native OpenShift secrets:

```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: azure-keyvault-payment-secrets
  namespace: payment-app
spec:
  provider: azure
  secretObjects:
    - secretName: app-secrets
      type: Opaque
      data:
        - objectName: payment-api-user
          key: APP_SECURITY_USER
        - objectName: payment-api-password
          key: APP_SECURITY_PASSWORD
```

### 4.2 In-Pod Volume Mount & Environment Variable Binding
The deployment mounts the volume to trigger CSI synchronization:

```yaml
volumeMounts:
  - name: secrets-store-inline
    mountPath: "/mnt/secrets-store"
    readOnly: true
volumes:
  - name: secrets-store-inline
    csi:
      driver: secrets-store.csi.k8s.io
      readOnly: true
      volumeAttributes:
        secretProviderClass: "azure-keyvault-payment-secrets"
```

Spring Boot consumes the synchronized secret via standard `envFrom`:

```yaml
envFrom:
  - secretRef:
      name: app-secrets
```

---

## 5. Secret Rotation Procedure

When credentials are rotated in Azure Key Vault:

1. **Update Secret in Azure Key Vault**:
   ```bash
   az keyvault secret set \
     --vault-name kv-enterprise-secrets \
     --name "payment-api-password" \
     --value "<NEW_COMPLEX_PASSWORD>"
   ```
2. **CSI Driver Auto-Rotation**:
   The Secrets Store CSI Driver periodically polls Azure Key Vault (default interval: 2 minutes), updates `/mnt/secrets-store`, and refreshes the native Kubernetes secret `app-secrets`.
3. **Perform Rolling Deployment Restart**:
   ```bash
   # On-Premises
   oc rollout restart deployment/payment-service -n payment-app
   oc rollout restart deployment/transaction-service -n payment-app

   # ARO
   oc rollout restart deployment/trade-service -n trade-app
   oc rollout restart deployment/portfolio-service -n trade-app
   ```
