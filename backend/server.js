require('dotenv').config();
const http = require('http');
const express = require('express');
const cors = require('cors');
const auth = require('./auth');
const vmManager = require('./vmManager');
const displayBridge = require('./displayBridge');

const app = express();
const server = http.createServer(app);

app.use(cors());
app.use(express.json());

// Request logging
app.use((req, res, next) => {
  console.log(`[HTTP] ${req.method} ${req.url}`);
  next();
});

// Middleware: Authenticate Bearer Session Token
function requireAuth(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Missing or malformed Authorization header' });
  }

  const token = authHeader.substring(7);
  if (!auth.validateSession(token)) {
    return res.status(401).json({ error: 'Invalid or expired session token' });
  }

  req.sessionToken = token;
  next();
}

/**
 * POST /login
 * Authenticates client and issues short-lived session token.
 */
app.post('/login', (req, res) => {
  const { token, client, version } = req.body;
  const sessionToken = auth.authenticate(token);

  if (!sessionToken) {
    return res.status(401).json({ error: 'Invalid authentication credentials' });
  }

  console.log(`[Auth] Client authenticated: ${client || 'unknown'} (v${version || '1.0'})`);
  res.json({
    status: 'AUTHENTICATED',
    sessionToken,
    expiresInSeconds: 7200
  });
});

/**
 * POST /vm/start
 * Requests the Cloud VM Manager to boot an instance.
 */
app.post('/vm/start', requireAuth, async (req, res) => {
  try {
    const config = req.body;
    console.log(`[VM] Starting Cloud VM on provider: ${config.provider || 'ORACLE_FREE_TIER'}`);

    const vmStatus = await vmManager.startVm(config, req.sessionToken);
    res.json(vmStatus);
  } catch (err) {
    console.error('[VM] Failed to start VM:', err.message);
    res.status(500).json({ error: err.message });
  }
});

/**
 * GET /vm/status
 * Queries current VM status and resource usage.
 */
app.get('/vm/status', requireAuth, async (req, res) => {
  try {
    const vmId = req.query.vmId;
    if (!vmId) {
      return res.status(400).json({ error: 'Missing vmId parameter' });
    }

    const status = await vmManager.getVmStatus(vmId);
    if (status.state === 'TERMINATED') {
      return res.status(404).json(status);
    }

    res.json(status);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /vm/connect
 * Acquires a display session ticket for connecting to the remote display stream.
 */
app.post('/vm/connect', requireAuth, async (req, res) => {
  const { vmId } = req.body;
  const status = await vmManager.getVmStatus(vmId);

  if (status.state !== 'RUNNING') {
    return res.status(400).json({ error: 'VM is not in RUNNING state' });
  }

  res.json({
    vmId,
    sessionTicket: status.sessionTicket || `ticket_${Date.now()}`,
    wsUrl: `/display/stream?ticket=${status.sessionTicket}`
  });
});

/**
 * POST /vm/stop
 * Shuts down the VM and frees cloud resources.
 */
app.post('/vm/stop', requireAuth, async (req, res) => {
  const { vmId, releaseResources } = req.body;
  console.log(`[VM] Stopping VM ${vmId} (release resources: ${releaseResources})`);

  const stopped = await vmManager.stopVm(vmId);
  res.json({ success: stopped, vmId, state: 'STOPPED' });
});

/**
 * POST /vm/disconnect
 * Disconnects client session while leaving the VM running in cloud.
 */
app.post('/vm/disconnect', requireAuth, (req, res) => {
  const { vmId } = req.body;
  console.log(`[VM] Client disconnected from VM ${vmId} (VM remains running)`);
  res.json({ success: true, vmId, state: 'RUNNING' });
});

// Attach WebSocket display bridge
displayBridge.attach(server);

const PORT = process.env.PORT || 8080;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`=======================================================`);
  console.log(`CloudPocket VM Backend Server running on port ${PORT}`);
  console.log(`Operating System: ChromiumOS / Lightweight Linux Workspace`);
  console.log(`Default Provider: Oracle Cloud Always Free / Self-Hosted Docker`);
  console.log(`Idle Auto-Shutdown: 15 minutes`);
  console.log(`=======================================================`);
});
