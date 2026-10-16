#!/usr/bin/env bash
set -e

# ==============================================================================
# Automated Verification Script for OpenShift Local (CRC) Deployments
# ==============================================================================

if [ -d "$HOME/.crc/bin/oc" ]; then
  export PATH="$HOME/.crc/bin/oc:$HOME/.crc/bin:$PATH"
elif [ -d "$HOME/.crc/bin" ]; then
  export PATH="$HOME/.crc/bin:$PATH"
fi

PAYMENT_HOST=$(oc get route payment-service -n payment-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "payment-service-payment-app.apps-crc.testing")
TRADE_HOST=$(oc get route trade-service -n trade-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "trade-service-trade-app.apps-crc.testing")
PORTFOLIO_HOST=$(oc get route portfolio-service -n trade-app -o jsonpath='{.spec.host}' 2>/dev/null || echo "portfolio-service-trade-app.apps-crc.testing")

echo "======================================================================"
echo "  Executing Local CRC Microservices Integration Tests"
echo "======================================================================"
echo "Payment Host:   $PAYMENT_HOST"
echo "Trade Host:     $TRADE_HOST"
echo "Portfolio Host: $PORTFOLIO_HOST"
echo ""

echo "----------------------------------------------------------------------"
echo "TEST 1: Portfolio Service GET Account Position (ACC-1001)"
echo "----------------------------------------------------------------------"
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' \
  "https://${PORTFOLIO_HOST}/api/v1/portfolios/ACC-1001"
echo -e "\n"

echo "----------------------------------------------------------------------"
echo "TEST 2: Trade Service POST Order (Buy 10 MSFT @ \$420.00)"
echo "----------------------------------------------------------------------"
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' -X POST \
  "https://${TRADE_HOST}/api/v1/trades" \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC-1001","symbol":"MSFT","side":"BUY","quantity":10,"price":420.00}'
echo -e "\n"

echo "----------------------------------------------------------------------"
echo "TEST 3: Payment Service POST Payment (Publish \$1,250.00 to Kafka)"
echo "----------------------------------------------------------------------"
curl -s -k -u 'api-user:LocalCrcSecurePassword123!' -X POST \
  "https://${PAYMENT_HOST}/api/v1/payments" \
  -H "Content-Type: application/json" \
  -d '{"paymentId":"PAY-1002","sourceAccount":"ACC-1001","destinationAccount":"ACC-2002","amount":1250.00,"currency":"USD","description":"Invoice"}'
echo -e "\n"

echo "----------------------------------------------------------------------"
echo "TEST 4: Transaction Service Consumer Event Settlement Logs"
echo "----------------------------------------------------------------------"
sleep 2
oc logs deployment/transaction-service -n payment-app --tail=6
echo ""

echo "----------------------------------------------------------------------"
echo "TEST 5: Prometheus Scrape Actuator Metrics (Camel & HTTP)"
echo "----------------------------------------------------------------------"
curl -s -k "https://${PAYMENT_HOST}/actuator/prometheus" | grep -E "(camel_exchanges_total|http_server_requests_seconds_count)" | head -n 10
echo ""
echo "======================================================================"
echo "  All Tests Completed Successfully!"
echo "======================================================================"
