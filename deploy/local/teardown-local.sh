#!/usr/bin/env bash
set -e

# ==============================================================================
# Teardown Script for OpenShift Local (CRC) Deployments
# ==============================================================================

echo "==> Deleting local-crc namespaces and workloads..."

oc delete namespace payment-app --ignore-not-found=true
oc delete namespace trade-app --ignore-not-found=true

echo "[+] Successfully removed payment-app and trade-app namespaces."
