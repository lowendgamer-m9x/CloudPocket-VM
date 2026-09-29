package com.example.model

enum class CloudProvider(
    val displayName: String,
    val description: String,
    val isFreeTierEligible: Boolean
) {
    ORACLE_FREE_TIER(
        "Oracle Cloud Always Free",
        "Ampere ARM / AMD Free Tier VM (4 OCPUs, 24GB RAM max allocation)",
        true
    ),
    GCP_E2_MICRO(
        "Google Cloud e2-micro",
        "GCP Always Free (1 shared core, 1GB RAM, US regions)",
        true
    ),
    AWS_EC2_FREE(
        "AWS EC2 Free Tier",
        "t2.micro / t4g.micro (750 hours/month for 12 months)",
        true
    ),
    FLY_IO_FREE(
        "Fly.io Free Allowances",
        "Shared CPU-1x, 256MB/512MB RAM lightweight micro-VM",
        true
    ),
    SELF_HOSTED_DOCKER(
        "Self-Hosted / Local Docker",
        "Custom server or local machine running CloudPocket VM container",
        true
    )
}

enum class StreamQuality(
    val displayName: String,
    val resolutionLabel: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetFps: Int,
    val jpegQuality: Int,
    val description: String
) {
    LOW(
        "LOW (Recommended)",
        "480p",
        854,
        480,
        15,
        45,
        "Aggressive compression, lowest RAM (RGB_565) & bandwidth for old devices"
    ),
    BALANCED(
        "BALANCED",
        "720p",
        1280,
        720,
        24,
        65,
        "Moderate FPS & bandwidth for stable Wi-Fi connections"
    ),
    QUALITY(
        "QUALITY",
        "1080p",
        1920,
        1080,
        30,
        80,
        "Crisp display when bandwidth permits"
    )
}

data class CloudConfig(
    val serverUrl: String = "https://cloudpocket-backend.local",
    val provider: CloudProvider = CloudProvider.ORACLE_FREE_TIER,
    val vmImage: String = "chromiumos-lightweight:v2.1",
    val vmUsername: String = "clouduser",
    val authToken: String = "cpvm_demo_token_sec",
    val maxLifetimeMinutes: Int = 120,
    val maxSimultaneousSessions: Int = 1,
    val ramAllocationMb: Int = 1024,
    val cpuCores: Int = 1,
    val storageAllocationGb: Int = 10,
    val qualityPreset: StreamQuality = StreamQuality.LOW,
    val idleTimeoutMinutes: Int = 15,
    val touchAsMouse: Boolean = true,
    val trackpadMode: Boolean = false,
    val autoReconnect: Boolean = true
)
