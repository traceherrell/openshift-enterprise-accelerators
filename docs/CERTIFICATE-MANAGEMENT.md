# Certificate Management & HTTPS Guide

This document describes how HTTPS and SSL/TLS certificates are managed for the microservices running on **On-Premises OpenShift** and **Azure Red Hat OpenShift (ARO)**.

---

## 1. TLS Architecture Patterns on OpenShift

```
Pattern A: Edge Route Termination (Standard / Recommended)
Client (HTTPS:443) ──► OpenShift Ingress Router (TLS Terminated) ──► Pod (HTTP:8443)

Pattern B: End-to-End Re-encrypt via Internal Service CA
Client (HTTPS:443) ──► OpenShift Ingress Router (Re-encrypts) ────► Pod (HTTPS:8443)
```

---

### Pattern A: Edge Route Termination (Standard / Recommended)
In this pattern, the OpenShift Ingress Router terminates the public TLS connection using the enterprise certificate configured on the OpenShift cluster or route. Traffic between the Ingress Router and the pod travels securely across the internal OpenShift Software-Defined Network (SDN).

The declarative route manifest across both environments demonstrates this:
```yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: payment-service
  namespace: payment-app
spec:
  to:
    kind: Service
    name: payment-service
  port:
    targetPort: https
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
```

- **On-Premises OCP**: Uses the enterprise wildcard ingress certificate configured in the IngressController.
- **ARO**: Automatically inherits the trusted Microsoft-managed wildcard certificate covering `*.apps.<cluster-name>.<region>.aroapp.io`.

---

### Pattern B: End-to-End TLS via OpenShift Service Serving Certificates
For high-security regulatory requirements where traffic must be encrypted up to the container pod:

1. **Annotate Service for Automatic CA Certificate Injection**:
   OpenShift includes an internal CA service that automatically generates and rotates TLS certificates. Annotate the service:
   ```yaml
   metadata:
     annotations:
       service.beta.openshift.io/serving-cert-secret-name: payment-service-tls
   ```
   OpenShift creates a native secret named `payment-service-tls` containing `tls.crt` and `tls.key`.

2. **Mount the Certificate into the Deployment**:
   ```yaml
   volumeMounts:
     - name: tls-certs
       mountPath: /var/run/secrets/tls
       readOnly: true
   volumes:
     - name: tls-certs
       secret:
         secretName: payment-service-tls
   ```

3. **Configure Re-encrypt Route**:
   ```yaml
   spec:
     tls:
       termination: reencrypt
       destinationCACertificate: |
         -----BEGIN CERTIFICATE-----
         ... (OpenShift Service CA Certificate) ...
         -----END CERTIFICATE-----
   ```

---

### Pattern C: Custom Domain Certificates Managed in Azure Key Vault
For enterprise custom domain names (e.g., `api.banking.example.com`):

1. **Store the Enterprise Certificate in Azure Key Vault**:
   ```bash
   az keyvault certificate import \
     --vault-name kv-enterprise-secrets \
     --name "enterprise-custom-domain-tls" \
     --file domain.pfx
   ```

2. **Sync to OpenShift Route as `kubernetes.io/tls` Secret**:
   Configure the `SecretProviderClass` to synchronize the certificate directly into a native TLS secret:
   ```yaml
   secretObjects:
     - secretName: custom-domain-tls
       type: kubernetes.io/tls
       data:
         - objectName: enterprise-custom-domain-tls
           key: tls.key
         - objectName: enterprise-custom-domain-tls
           key: tls.crt
   ```

---

## 2. Generating a Self-Signed PKCS12 Keystore for Local Testing

If testing in an isolated environment without an ingress router:

```bash
# Generate private key and self-signed certificate in PKCS12 format
keytool -genkeypair \
  -alias payment-service \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 365 \
  -storepass "${SSL_KEYSTORE_PASSWORD}" \
  -dname "CN=payment-service.payment-app.svc.cluster.local, OU=Engineering, O=Enterprise, C=US"

# Note: keystore.p12 is automatically excluded by .gitignore and must never be committed.
```

---

## 3. AMQ Streams (Kafka) TLS Truststore Configuration

When connecting to an AMQ Streams Kafka broker configured with TLS (`SSL` or `SASL_SSL`):

```bash
# 1. Extract the AMQ Streams Cluster CA Certificate
oc extract secret/amq-cluster-cluster-ca-cert --keys=ca.crt --to=- -n amq-streams > amq-ca.crt

# 2. Import CA certificate into Java Truststore
keytool -import \
  -trustcacerts \
  -alias amq-cluster-ca \
  -file amq-ca.crt \
  -keystore kafka-truststore.jks \
  -storepass "${KAFKA_SSL_TRUSTSTORE_PASSWORD}" \
  -noprompt

# 3. Store the truststore password in Azure Key Vault
az keyvault secret set \
  --vault-name kv-enterprise-secrets \
  --name "kafka-truststore-password" \
  --value "${KAFKA_SSL_TRUSTSTORE_PASSWORD}"
```
