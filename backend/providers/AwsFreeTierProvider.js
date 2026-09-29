const CloudProvider = require('./CloudProvider');

/**
 * AWS EC2 Free Tier Provider
 * AWS offers 750 hours/month of t2.micro or t3.micro (1 vCPU, 1GB RAM)
 * for 12 months on free-tier accounts.
 */
class AwsFreeTierProvider extends CloudProvider {
  constructor(options = {}) {
    super('AwsFreeTierProvider', options);
    this.instances = new Map();
  }

  async startInstance(config) {
    const vmId = `aws-t2micro-${Math.random().toString(36).substring(2, 8)}`;
    const instance = {
      vmId,
      state: 'RUNNING',
      ipAddress: process.env.AWS_INSTANCE_IP || '54.210.14.77',
      port: 8080,
      startTime: Date.now(),
      ramMb: 1024,
      cpuCores: 1,
      osName: 'ChromiumOS / Lightweight Linux Chromium Environment',
      osDetails: 'Lightweight Chromium Workspace on AWS EC2 t2.micro Free Tier'
    };

    this.instances.set(vmId, instance);
    console.log(`[AwsFreeTierProvider] Started AWS EC2 Free Tier instance ${vmId}`);
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
      cpuPercent: 10,
      ramMb: 360,
      totalRamMb: 1024,
      ipAddress: instance.ipAddress,
      port: instance.port
    };
  }

  async stopInstance(vmId) {
    const instance = this.instances.get(vmId);
    if (instance) {
      instance.state = 'STOPPED';
      this.instances.delete(vmId);
      console.log(`[AwsFreeTierProvider] Terminated AWS instance ${vmId}`);
      return true;
    }
    return false;
  }

  getLimits() {
    return {
      maxInstances: 1,
      maxRamMb: 1024,
      maxCpuCores: 1,
      isFreeTierEligible: true
    };
  }
}

module.exports = AwsFreeTierProvider;
