#!/usr/bin/env bash
set -e

# ==============================================================================
# Automated Local Deployment Script for OpenShift Local (CRC)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "==================================================================="
echo "  Deploying Reference Microservices to OpenShift Local (CRC)"
echo "==================================================================="

# 1. Verify oc CLI and cluster connectivity
if [ -d "$HOME/.crc/bin/oc" ]; then
  export PATH="$HOME/.crc/bin/oc:$HOME/.crc/bin:$PATH"
elif [ -d "$HOME/.crc/bin" ]; then
  export PATH="$HOME/.crc/bin:$PATH"
fi
if command -v crc &> /dev/null; then
  eval $(crc oc-env 2>/dev/null || true)
fi

if ! command -v oc &> /dev/null; then
  echo "[-] ERROR: 'oc' CLI is not found in PATH."
  echo "    Run: eval \$(crc oc-env)"
  exit 1
fi

echo "==> Verifying OpenShift cluster connectivity..."
if ! oc whoami &> /dev/null; then
  echo "[-] ERROR: You are not currently logged into OpenShift."
  echo "    Please log in first using:"
  echo "    oc login -u kubeadmin https://api.crc.testing:6443"
  exit 1
fi
echo "[+] Connected as $(oc whoami) to $(oc whoami --show-server)"

# 2. Create Namespaces
echo "==> Creating application namespaces (payment-app, trade-app)..."
oc apply -f "$SCRIPT_DIR/01-namespaces.yaml"

# 3. Apply Local Secrets & ConfigMaps
echo "==> Applying local developer secrets and ConfigMaps..."
oc apply -f "$SCRIPT_DIR/02-local-secrets.yaml"
oc apply -f "$SCRIPT_DIR/03-configmaps.yaml"

# 4. Deploy Ephemeral Kafka
echo "==> Deploying single-node ephemeral Kafka (KRaft mode)..."
oc apply -f "$SCRIPT_DIR/04-kafka-ephemeral.yaml"

# 5. Compile JARs locally with Maven
echo "==> Compiling microservice JAR packages locally..."
mvn clean package -DskipTests -f "$WORKSPACE_ROOT/On-Prem-Microservice-1/pom.xml" -q
mvn clean package -DskipTests -f "$WORKSPACE_ROOT/On-Prem-Microservice-2/pom.xml" -q
mvn clean package -DskipTests -f "$WORKSPACE_ROOT/ARO-Microservice-1/pom.xml" -q
mvn clean package -DskipTests -f "$WORKSPACE_ROOT/ARO-Microservice-2/pom.xml" -q
echo "[+] All JAR packages compiled successfully."

# 6. Build and Deploy Images via OpenShift Binary BuildConfig
echo "==> Setting up OpenShift ImageStreams and BuildConfigs..."

build_service() {
  local service_name="$1"
  local namespace="$2"
  local service_dir="$3"

  echo "-------------------------------------------------------------------"
  echo "--> Building container image for $service_name in namespace $namespace..."
  
  # Ensure ImageStream exists
  oc create is "$service_name" -n "$namespace" --dry-run=client -o yaml | oc apply -f -

  # Create BuildConfig if not already present
  if ! oc get bc "$service_name" -n "$namespace" &> /dev/null; then
    oc new-build --name "$service_name" --binary --strategy=docker -n "$namespace"
  fi

  # Trigger binary build from the service directory
  echo "--> Uploading pre-built artifact and creating image for $service_name..."
  oc start-build "$service_name" --from-dir="$service_dir" --follow -n "$namespace"

  # Clean up completed build pod to conserve CRC node ephemeral storage
  oc delete build -l "buildconfig=$service_name" -n "$namespace" --wait=false &> /dev/null || true
}

# Build Banking services
build_service "payment-service" "payment-app" "$WORKSPACE_ROOT/On-Prem-Microservice-1"
build_service "transaction-service" "payment-app" "$WORKSPACE_ROOT/On-Prem-Microservice-2"

# Build Trading services
build_service "portfolio-service" "trade-app" "$WORKSPACE_ROOT/ARO-Microservice-1"
build_service "trade-service" "trade-app" "$WORKSPACE_ROOT/ARO-Microservice-2"

# 7. Deploy Applications and Routes
echo "-------------------------------------------------------------------"
echo "==> Deploying microservice workloads, services, and routes..."
oc apply -f "$SCRIPT_DIR/05-onprem-apps.yaml"
oc apply -f "$SCRIPT_DIR/06-aro-apps.yaml"
oc apply -f "$SCRIPT_DIR/07-routes.yaml"

# Trigger rolling restart to load newly pushed images
oc rollout restart deployment/payment-service -n payment-app
oc rollout restart deployment/transaction-service -n payment-app
oc rollout restart deployment/portfolio-service -n trade-app
oc rollout restart deployment/trade-service -n trade-app

# 8. Apply ServiceMonitors
echo "==> Applying Prometheus ServiceMonitors..."
oc apply -f "$SCRIPT_DIR/08-servicemonitor.yaml" || true

echo "==================================================================="
echo "  Deployment Complete! Fetching Routes & Credentials"
echo "==================================================================="

PAYMENT_HOST=$(oc get route payment-service -n payment-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "payment-service-payment-app.apps-crc.testing")
TRADE_HOST=$(oc get route trade-service -n trade-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "trade-service-trade-app.apps-crc.testing")
PORTFOLIO_HOST=$(oc get route portfolio-service -n trade-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "portfolio-service-trade-app.apps-crc.testing")

echo ""
echo "Local Test Credentials:"
echo "  Username: api-user"
echo "  Password: LocalCrcSecurePassword123!"
echo ""
echo "Endpoints Available on CRC:"
echo "  1. Payment Service:   https://${PAYMENT_HOST}/api/v1/payments"
echo "  2. Trade Service:     https://${TRADE_HOST}/api/v1/trades"
echo "  3. Portfolio Service: https://${PORTFOLIO_HOST}/api/v1/portfolios/ACC-1001"
echo ""
echo "Quick Test Commands:"
echo ""
echo "--> Test 1: Ingest Payment into Kafka (Payment Service):"
echo "curl -k -u \"api-user:LocalCrcSecurePassword123!\" -X POST https://${PAYMENT_HOST}/api/v1/payments \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"paymentId\":\"PAY-1001\",\"senderIban\":\"US12BANK0001\",\"recipientIban\":\"US12BANK0002\",\"amount\":2500.00,\"currency\":\"USD\"}'"
echo ""
echo "--> Test 2: Query Portfolio Position:"
echo "curl -k -u \"api-user:LocalCrcSecurePassword123!\" https://${PORTFOLIO_HOST}/api/v1/portfolios/ACC-1001"
echo ""
echo "--> Test 3: Execute Trade (Invokes Portfolio Service via Camel HTTP):"
echo "curl -k -u \"api-user:LocalCrcSecurePassword123!\" -X POST https://${TRADE_HOST}/api/v1/trades \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"accountId\":\"ACC-1001\",\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":50,\"price\":225.50}'"
echo ""
echo "--> Test 4: Check Prometheus Metrics Endpoint:"
echo "curl -k https://${PAYMENT_HOST}/actuator/prometheus | grep -E \"(http_server_requests|camel_exchanges)\""
echo "==================================================================="
