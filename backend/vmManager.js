const DockerProvider = require('./providers/DockerProvider');
const OracleFreeTierProvider = require('./providers/OracleFreeTierProvider');
const GcpFreeTierProvider = require('./providers/GcpFreeTierProvider');
const AwsFreeTierProvider = require('./providers/AwsFreeTierProvider');

/**
 * Cloud VM Manager
 * Coordinates VM lifecycle, idle timeouts, session limits, and provider drivers.
 */
class VmManager {
  constructor() {
    this.providers = {
      SELF_HOSTED_DOCKER: new DockerProvider(),
      ORACLE_FREE_TIER: new OracleFreeTierProvider(),
      GCP_E2_MICRO: new GcpFreeTierProvider(),
      AWS_EC2_FREE: new AwsFreeTierProvider()
    };

    this.activeVms = new Map(); // vmId -> VM record
    this.idleTimers = new Map(); // vmId -> timeout
  }

  getProvider(providerName) {
    return this.providers[providerName] || this.providers.ORACLE_FREE_TIER;
  }

  async startVm(config, sessionToken) {
    const provider = this.getProvider(config.provider);
    const limits = provider.getLimits();

    // Check simultaneous session constraints
    if (this.activeVms.size >= (config.maxSimultaneousSessions || limits.maxInstances)) {
      // Look for existing running VM for session reuse
      const firstEntry = Array.from(this.activeVms.values())[0];
      if (firstEntry && firstEntry.state === 'RUNNING') {
        this.resetIdleTimer(firstEntry.vmId, config.idleTimeoutMinutes || 15);
        return firstEntry;
      }
    }

    const instance = await provider.startInstance(config);
    const vmRecord = {
      ...instance,
      provider: config.provider,
      sessionToken,
      sessionTicket: `ticket_${Date.now()}`,
      idleTimeoutMinutes: config.idleTimeoutMinutes || 15,
      maxLifetimeMinutes: config.maxLifetimeMinutes || 120,
      lastActivityTime: Date.now(),
      osName: 'ChromiumOS (Open-Source Chromium Environment)',
      osDetails: 'Lightweight Google Chromium-based OS with Kiosk & Web App Runtime (Not proprietary Google ChromeOS)'
    };

    this.activeVms.set(instance.vmId, vmRecord);
    this.resetIdleTimer(instance.vmId, vmRecord.idleTimeoutMinutes);

    return vmRecord;
  }

  async getVmStatus(vmId) {
    const vm = this.activeVms.get(vmId);
    if (!vm) {
      return {
        vmId,
        state: 'TERMINATED',
        errorMessage: 'Cloud computer is no longer available.'
      };
    }

    const provider = this.getProvider(vm.provider);
    const providerStatus = await provider.getInstanceStatus(vmId);

    const idleElapsedMinutes = Math.floor((Date.now() - vm.lastActivityTime) / 60000);
    const idleRemainingSeconds = Math.max(0, (vm.idleTimeoutMinutes * 60) - Math.floor((Date.now() - vm.lastActivityTime) / 1000));

    return {
      ...vm,
      ...providerStatus,
      idleRemainingSeconds
    };
  }

  recordActivity(vmId) {
    const vm = this.activeVms.get(vmId);
    if (vm) {
      vm.lastActivityTime = Date.now();
      this.resetIdleTimer(vmId, vm.idleTimeoutMinutes);
    }
  }

  resetIdleTimer(vmId, minutes) {
    if (this.idleTimers.has(vmId)) {
      clearTimeout(this.idleTimers.get(vmId));
    }

    const timeout = setTimeout(async () => {
      console.log(`[VmManager] Idle timeout expired for ${vmId} (${minutes}m of inactivity). Reaping VM.`);
      await this.stopVm(vmId);
    }, minutes * 60 * 1000);

    this.idleTimers.set(vmId, timeout);
  }

  async stopVm(vmId) {
    const vm = this.activeVms.get(vmId);
    if (!vm) return false;

    if (this.idleTimers.has(vmId)) {
      clearTimeout(this.idleTimers.get(vmId));
      this.idleTimers.delete(vmId);
    }

    const provider = this.getProvider(vm.provider);
    await provider.stopInstance(vmId);
    this.activeVms.delete(vmId);
    return true;
  }
}

module.exports = new VmManager();
