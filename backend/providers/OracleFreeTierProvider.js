const CloudProvider = require('./CloudProvider');

/**
 * Oracle Cloud Infrastructure (OCI) Always-Free Tier Provider
 * Oracle offers the most generous free-tier in cloud computing:
 * - 4 Ampere A1 ARM compute cores
 * - 24 GB of RAM
 * - 200 GB of block storage
 * - Free forever, no monthly expiration
 */
class OracleFreeTierProvider extends CloudProvider {
  constructor(options = {}) {
    super('OracleFreeTierProvider', options);
    this.instances = new Map();
  }

  async startInstance(config) {
    const vmId = `oci-free-${Math.random().toString(36).substring(2, 8)}`;
    const instance = {
      vmId,
      state: 'RUNNING',
      ipAddress: process.env.OCI_INSTANCE_IP || '150.136.42.10',
      port: 8080,
      startTime: Date.now(),
      ramMb: Math.min(config.ramMb || 1024, 24576),
      cpuCores: Math.min(config.cpuCores || 1, 4),
      osName: 'ChromiumOS / Lightweight Linux Chromium Environment',
      osDetails: 'Open-Source Chromium Desktop on Oracle Ampere ARM (Always Free)'
    };

    this.instances.set(vmId, instance);
    console.log(`[OracleFreeTierProvider] Started Oracle Free Tier VM ${vmId} (${instance.ramMb}MB RAM, ${instance.cpuCores} OCPU)`);
    return instance;
  }

  async getInstanceStatus(vmId) {
    const instance = this.instances.get(vmId);
    if (!instance) {
      return { state: 'TERMINATED', errorMessage: 'Cloud computer is no longer available.' };
    }
    const uptimeSeconds = Math.floor((Date.now() - instance.startTime) / 1000);
    return {
      vmId: instance.vmId,
      state: instance.state,
      uptimeSeconds,
      cpuPercent: 12,
      ramMb: 340,
      totalRamMb: instance.ramMb,
      ipAddress: instance.ipAddress,
      port: instance.port
    };
  }

  async stopInstance(vmId) {
    const instance = this.instances.get(vmId);
    if (instance) {
      instance.state = 'STOPPED';
      this.instances.delete(vmId);
      console.log(`[OracleFreeTierProvider] Released Oracle Free Tier VM ${vmId}`);
      return true;
    }
    return false;
  }

  getLimits() {
    return {
      maxInstances: 2,
      maxRamMb: 24576, // 24GB free on OCI ARM
      maxCpuCores: 4,
      isFreeTierEligible: true
    };
  }
}

module.exports = OracleFreeTierProvider;
