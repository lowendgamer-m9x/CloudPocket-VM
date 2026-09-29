# CloudPocket VM - Cloud Backend & VM Manager

This backend manages the cloud virtual computer lifecycle and streams the remote desktop to the CloudPocket VM Android thin client.

## Operating System Transparency

The environment executes an **open-source Chromium-based desktop** running on Alpine/Debian Linux with an X11 virtual display buffer and Chromium browser kiosk.
- **Operating System:** ChromiumOS / Open-Source Google Chromium-based Environment
- **Labeling Policy:** Explicitly open-source Chromium; not proprietary Google ChromeOS.
- **Security:** Sandboxed container, token-based authentication, non-root execution.

---

## Free-Tier Cloud Deployment Guide

CloudPocket VM is architected to minimize cost and run on free-tier cloud resources without requiring recurring expenses.

### 1. Oracle Cloud Always Free (Recommended)
Oracle provides the most generous free-tier in cloud computing:
- **Specs:** 4 Ampere A1 ARM compute cores, 24 GB RAM, 200 GB SSD storage.
- **Cost:** Free forever (no 12-month expiration).
- **Deployment:**
  1. Create a free account at [oracle.com/cloud/free](https://www.oracle.com/cloud/free).
  2. Launch an Ampere A1 instance (Ubuntu 22.04 or Oracle Linux).
  3. Install Docker: `curl -fsSL https://get.docker.com | sh`
  4. Clone or copy `/backend` directory.
  5. Run: `docker compose up -d`
  6. Open port `8080` in OCI Virtual Cloud Network (VCN) Ingress Rules.

### 2. Google Cloud Platform (GCP) e2-micro Always Free
- **Specs:** 1 e2-micro VM (2 vCPUs, 1GB RAM) in `us-central1`, `us-west1`, or `us-east1`.
- **Cost:** 744 hours/month free forever.
- **Deployment:**
  ```bash
  gcloud compute instances create-with-container cloudpocket-vm \
      --zone=us-central1-a \
      --machine-type=e2-micro \
      --container-image=your-registry/cloudpocket-vm:latest
  ```

### 3. AWS EC2 Free Tier (t2.micro / t4g.micro)
- **Specs:** 750 hours/month for 12 months.
- **Deployment:**
  Launch a `t4g.micro` (ARM) or `t2.micro` (x86) instance with Amazon Linux 2023 or Ubuntu, clone `/backend`, and run `npm start`.

### 4. Self-Hosted / Local Docker
Run directly on your desktop or home server:
```bash
cd backend
npm install
npm start
```
Or with Docker:
```bash
docker build -t cloudpocket-vm .
docker run -p 8080:8080 cloudpocket-vm
```

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/login` | Authenticates thin client and issues session token |
| `POST` | `/vm/start` | Boots cloud VM or resumes dormant session |
| `GET` | `/vm/status` | Returns VM IP, uptime, CPU, and RAM metrics |
| `POST` | `/vm/connect` | Issues display ticket for WebSocket connection |
| `POST` | `/vm/stop` | Shuts down cloud VM and releases cloud credits |
| `POST` | `/vm/disconnect` | Leaves VM running while closing client stream |
| `WS` | `/display/stream` | Low-bandwidth binary JPEG frame stream & input |

---

## Automatic Idle Reaping
To prevent accidental cloud charges if a user forgets an active session, the backend enforces a configurable **15-minute idle timeout**. If no touch or keyboard inputs are received within 15 minutes, the VM automatically shuts down.
