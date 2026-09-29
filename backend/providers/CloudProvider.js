/**
 * CloudProvider Base Interface
 * Modular abstraction layer allowing CloudPocket VM to orchestrate VMs
 * across different free-tier and self-hosted cloud backends.
 */
class CloudProvider {
  constructor(name, options = {}) {
    this.name = name;
    this.options = options;
  }

  /**
   * Provisions and boots a cloud VM instance.
   * @param {Object} config - VM configuration (RAM, CPU, image, etc.)
   * @returns {Promise<{ vmId: string, ipAddress: string, port: number }>}
   */
  async startInstance(config) {
    throw new Error('startInstance() must be implemented by provider');
  }

  /**
   * Queries status and resource consumption of an instance.
   * @param {string} vmId
   * @returns {Promise<{ state: string, uptimeSeconds: number, cpuPercent: number, ramMb: number }>}
   */
  async getInstanceStatus(vmId) {
    throw new Error('getInstanceStatus() must be implemented by provider');
  }

  /**
   * Shuts down and releases compute resources.
   * @param {string} vmId
   * @returns {Promise<boolean>}
   */
  async stopInstance(vmId) {
    throw new Error('stopInstance() must be implemented by provider');
  }

  /**
   * Returns metadata and free-tier allocation limits.
   */
  getLimits() {
    return {
      maxInstances: 1,
      maxRamMb: 1024,
      maxCpuCores: 1,
      isFreeTierEligible: true
    };
  }
}

module.exports = CloudProvider;
