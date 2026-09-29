const crypto = require('crypto');

/**
 * Authentication and Session Ticket Management
 */
class AuthService {
  constructor() {
    this.validTokens = new Set([
      process.env.CPVM_AUTH_TOKEN || 'cpvm_demo_token_sec'
    ]);
    this.activeSessions = new Map(); // sessionToken -> sessionData
  }

  addAllowedToken(token) {
    this.validTokens.add(token);
  }

  authenticate(clientToken) {
    if (!clientToken || !this.validTokens.has(clientToken)) {
      return null;
    }

    const sessionToken = `st_${crypto.randomBytes(16).toString('hex')}`;
    const expiresAt = Date.now() + (120 * 60 * 1000); // 2 hours

    this.activeSessions.set(sessionToken, {
      createdAt: Date.now(),
      expiresAt,
      clientToken
    });

    return sessionToken;
  }

  validateSession(sessionToken) {
    if (!sessionToken) return false;
    const session = this.activeSessions.get(sessionToken);
    if (!session) return false;
    if (Date.now() > session.expiresAt) {
      this.activeSessions.delete(sessionToken);
      return false;
    }
    return true;
  }

  revokeSession(sessionToken) {
    this.activeSessions.delete(sessionToken);
  }
}

module.exports = new AuthService();
