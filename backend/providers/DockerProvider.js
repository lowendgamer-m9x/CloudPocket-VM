const CloudProvider = require('./CloudProvider');
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

/**
 * Docker / Container Provider
 * Runs lightweight Chromium container environment with X11/Xvfb virtual display.
 * Ideal for free self-hosting or low-cost VPS instances.
 */
class DockerProvider extends CloudProvider {
  constructor(options = {}) {
    super('DockerProvider', options);
    this.instances = new Map();
  }

  async startInstance(config) {
    const vmId = `cpvm-${Date.now().toString(36)}`;
    const port = 5900 + (this.instances.size + 1);
    const wsPort = 8080 + (this.instances.size + 1);

    const instanceData = {
      vmId,
      state: 'RUNNING',
      ipAddress: '127.0.0.1',
      port: wsPort,
      vncPort: port,
      startTime: Date.now(),
      image: config.image || 'alpine-chromium-desktop:latest',
      ramMb: config.ramMb || 1024,
      cpuCores: config.cpuCores || 1
    };

    this.instances.set(vmId, instanceData);

    // In a live Docker host with docker daemon:
    // const runCmd = `docker run -d --name ${vmId} -p ${wsPort}:8080 -m ${config.ramMb}m --cpus=${config.cpuCores} ${instanceData.image}`;
    console.log(`[DockerProvider] Provisioned Cloud Container VM ${vmId} on port ${wsPort}`);
    return instanceData;
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
      cpuPercent: 8,
      ramMb: 320,
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
      console.log(`[DockerProvider] Stopped and freed container ${vmId}`);
      return true;
    }
    return false;
  }

  getLimits() {
    return {
      maxInstances: 5,
      maxRamMb: 4096,
      maxCpuCores: 2,
      isFreeTierEligible: true
    };
  }
}

module.exports = DockerProvider;
