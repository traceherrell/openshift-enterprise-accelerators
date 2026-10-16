#!/usr/bin/env bash
# ==============================================================================
# Azure Key Vault Setup Script for Hybrid OpenShift (On-Premises & ARO)
# ==============================================================================
# This script provisions and populates Azure Key Vault with application secrets
# for both On-Premises OpenShift and Azure Red Hat OpenShift (ARO) microservices,
# and configures role-based access control (RBAC).
#
# Prerequisites:
# - az CLI installed and authenticated (az login)
# ==============================================================================

set -euo pipefail

RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-rg-enterprise-secrets}"
LOCATION="${AZURE_LOCATION:-eastus}"
KEYVAULT_NAME="${AZURE_KEYVAULT_NAME:-kv-enterprise-secrets}"

echo "==> Configuring Azure Key Vault: $KEYVAULT_NAME in $RESOURCE_GROUP ($LOCATION)"

# 1. Create Resource Group if it doesn't exist
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" -o table

# 2. Create Azure Key Vault with Azure RBAC enabled
echo "==> Ensuring Azure Key Vault exists..."
az keyvault create \
  --name "$KEYVAULT_NAME" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --enable-rbac-authorization true \
  -o table || echo "Key Vault $KEYVAULT_NAME already exists or access confirmed."

# 3. Populate Banking Platform Secrets (Consumed by On-Premises OpenShift)
echo "==> Storing Banking Platform secrets in Azure Key Vault..."
az keyvault secret set \
  --vault-name "$KEYVAULT_NAME" \
  --name "payment-api-user" \
  --value "api-user" -o none

az keyvault secret set \
  --vault-name "$KEYVAULT_NAME" \
  --name "payment-api-password" \
  --value "Secure_Vault_Password_$(openssl rand -hex 8)" -o none

az keyvault secret set \
  --vault-name "$KEYVAULT_NAME" \
  --name "kafka-truststore-password" \
  --value "Kafka_Truststore_Secret_$(openssl rand -hex 6)" -o none

az keyvault secret set \
  --vault-name "$KEYVAULT_NAME" \
  --name "logging-truststore-password" \
  --value "Logging_Truststore_Secret_$(openssl rand -hex 6)" -o none

# 4. Populate Trading Platform Secrets (Consumed by ARO)
echo "==> Storing Trading Platform secrets in Azure Key Vault..."
az keyvault secret set \
  --vault-name "$KEYVAULT_NAME" \
  --name "trade-api-user" \
  --value "api-user" -o none

az keyvault secret set \
  --vault-name "$KEYVAULT_NAME" \
  --name "trade-api-password" \
  --value "Secure_Vault_Password_$(openssl rand -hex 8)" -o none

# 5. Create / Configure Managed Identity or Service Principal for Cluster Access
echo "==> Creating User-Assigned Managed Identity for cluster access..."
IDENTITY_NAME="${AZURE_IDENTITY_NAME:-uami-openshift-keyvault}"
az identity create --name "$IDENTITY_NAME" --resource-group "$RESOURCE_GROUP" -o table || true

UAMI_CLIENT_ID=$(az identity show --name "$IDENTITY_NAME" --resource-group "$RESOURCE_GROUP" --query clientId -o tsv)
KEYVAULT_ID=$(az keyvault show --name "$KEYVAULT_NAME" --query id -o tsv)

echo "==> Assigning 'Key Vault Secrets User' role to Managed Identity ($UAMI_CLIENT_ID)..."
az role assignment create \
  --role "Key Vault Secrets User" \
  --assignee "$UAMI_CLIENT_ID" \
  --scope "$KEYVAULT_ID" -o table || true

echo "=============================================================================="
echo "Azure Key Vault configuration complete!"
echo "Vault Name:           $KEYVAULT_NAME"
echo "Resource Group:       $RESOURCE_GROUP"
echo "Managed Identity ID:  $UAMI_CLIENT_ID"
echo "Tenant ID:            $(az account show --query tenantId -o tsv)"
echo "Zero credentials committed to Git. Ready for On-Prem & ARO synchronization."
echo "=============================================================================="
