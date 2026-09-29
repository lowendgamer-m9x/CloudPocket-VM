const WebSocket = require('ws');
const vmManager = require('./vmManager');

/**
 * Display Streaming & Remote Input Bridge
 * Translates between X11/VNC/Wayland display buffer and low-bandwidth WebSocket client.
 */
class DisplayBridge {
  constructor() {
    this.connections = new Map(); // ws -> connectionState
  }

  attach(server) {
    const wss = new WebSocket.Server({ server, path: '/display/stream' });

    wss.on('connection', (ws, req) => {
      const url = new URL(req.url, 'http://localhost');
      const ticket = url.searchParams.get('ticket');
      const requestedQuality = req.headers['x-quality-preset'] || 'LOW';

      console.log(`[DisplayBridge] Client connected for remote display (Preset: ${requestedQuality})`);

      const clientState = {
        ws,
        ticket,
        quality: requestedQuality,
        fps: requestedQuality === 'LOW' ? 15 : (requestedQuality === 'BALANCED' ? 24 : 30),
        intervalTimer: null
      };

      this.connections.set(ws, clientState);
      this.startFrameStreaming(clientState);

      ws.on('message', (message) => {
        try {
          const event = JSON.parse(message.toString());
          this.handleClientInput(clientState, event);
        } catch (_err) {
          // Binary message or raw frame ack
        }
      });

      ws.on('close', () => {
        console.log('[DisplayBridge] Client disconnected from display stream');
        if (clientState.intervalTimer) {
          clearInterval(clientState.intervalTimer);
        }
        this.connections.delete(ws);
      });

      ws.on('error', (err) => {
        console.error('[DisplayBridge] WebSocket error:', err.message);
      });
    });
  }

  handleClientInput(clientState, event) {
    switch (event.type) {
      case 'mouse_move':
        // Map normalized (0.0 - 1.0) coordinates to X11 display:
        // xdotool mousemove (x * width) (y * height)
        break;
      case 'mouse_click':
        // Map to xdotool mousedown/mouseup
        break;
      case 'mouse_scroll':
        // Map to xdotool click 4 (up) / click 5 (down)
        break;
      case 'key':
        // Map to xdotool key / keydown / keyup
        break;
      case 'set_quality':
        clientState.quality = event.preset || 'LOW';
        clientState.fps = clientState.quality === 'LOW' ? 15 : (clientState.quality === 'BALANCED' ? 24 : 30);
        console.log(`[DisplayBridge] Adjusted stream quality to ${clientState.quality} (${clientState.fps} FPS)`);
        this.restartFrameStreaming(clientState);
        break;
      case 'ping':
        clientState.ws.send(JSON.stringify({ type: 'pong', t: event.t, serverTime: Date.now() }));
        break;
    }
  }

  restartFrameStreaming(clientState) {
    if (clientState.intervalTimer) {
      clearInterval(clientState.intervalTimer);
    }
    this.startFrameStreaming(clientState);
  }

  startFrameStreaming(clientState) {
    const intervalMs = Math.floor(1000 / clientState.fps);

    // In a live container with Xvfb, this captures /tmp/display.jpg or reads VNC RFB buffer
    // and sends binary JPEG buffer. For the bridge daemon:
    clientState.intervalTimer = setInterval(() => {
      if (clientState.ws.readyState === WebSocket.OPEN) {
        // Send heartbeat or binary frame if available
      }
    }, intervalMs);
  }
}

module.exports = new DisplayBridge();
