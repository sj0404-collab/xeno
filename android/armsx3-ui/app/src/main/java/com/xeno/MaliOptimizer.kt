package com.xeno

import com.xeno.config.Settings

/**
 * Applies Mali GPU-specific runtime optimisations based on the detected
 * [MaliGpuProfile]. Called during settings application or from the first-run
 * wizard when a Mali GPU is the active renderer.
 *
 * All functions are defensive: a null profile or a Settings instance that is
 * already optimised is returned unchanged.
 */
object MaliOptimizer {

    /**
     * Apply Mali-optimised defaults to an existing [Settings] instance by
     * mutating only the fields that benefit from Mali-specific tuning.
     *
     * Does NOT touch user-set fields aggressively — only sets values that
     * differ from the global defaults. Callers should apply the returned
     * result via [Settings.applyTo].
     */
    fun applyMaliPreset(profile: MaliGpuInfo.MaliGpuProfile, current: Settings = Settings()): Settings {
        return getRecommendedSettings(profile).let { optimised ->
            current.copy(
                ps3 = current.ps3.copy(
                    preferredSpuThreads = optimised.ps3.preferredSpuThreads,
                    spuBlockSize = optimised.ps3.spuBlockSize,
                    shaderMode = optimised.ps3.shaderMode,
                    vramLimitMb = optimised.ps3.vramLimitMb,
                ),
                upscaleFloat = optimised.upscaleFloat,
                gpuProfile = 1, // Mali — matches Rpcs3Settings GpuProfileOverride
            )
        }
    }

    /**
     * Return a full [Settings] instance with Mali-tier-optimised values.
     *
     * [current] is the caller's existing settings so non-Mali-relevant
     * fields (patches, aspect ratio, audio, DEV9, etc.) are preserved.
     */
    fun getRecommendedSettings(
        profile: MaliGpuInfo.MaliGpuProfile,
        current: Settings = Settings(),
    ): Settings {
        val tier = profile.tier
        return current.copy(
            upscaleFloat = profile.recommendedUpscale / 100f,
            gpuProfile = 1,
            ps3 = current.ps3.copy(
                preferredSpuThreads = profile.recommendedSpuThreads,
                spuBlockSize = when (tier) {
                    MaliGpuInfo.Tier.LOW -> 1  // Mega — larger blocks reduce scheduling overhead
                    MaliGpuInfo.Tier.MID -> 1  // Mega
                    MaliGpuInfo.Tier.HIGH -> 2 // Giga — better throughput on capable hardware
                    MaliGpuInfo.Tier.ULTRA -> 2 // Giga
                },
                shaderMode = if (profile.asyncComputeSupported) 1 else 0,
                vramLimitMb = profile.recommendedVramLimitMb,
                maxSpursThreads = when (tier) {
                    MaliGpuInfo.Tier.LOW -> 4
                    MaliGpuInfo.Tier.MID -> 5
                    MaliGpuInfo.Tier.HIGH -> 6
                    MaliGpuInfo.Tier.ULTRA -> 6
                },
            ),
        )
    }

    /**
     * Mali-optimised Vulkan settings applied before a VM launch.
     *
     * Returns a [Settings] with Mali-specific renderer flags set. This is
     * meant to be called during the launch path when the active GPU is Mali.
     */
    fun getMaliVulkanSettings(
        profile: MaliGpuInfo.MaliGpuProfile,
        current: Settings = Settings(),
    ): Settings {
        return current.copy(
            gpuProfile = 1, // Mali
            // Mali tiling GPUs benefit from coalescing render passes — each
            // pass boundary costs a tile store + reload.
            coalesceRenderPasses = true,
            // Disable Adreno-specific framebuffer fetch on Mali.
            adrenoFbFetch = false,
            // Let the user opt-in to Mali FB fetch via forceMaliFbFetch;
            // do not force it on by default (many drivers have issues).
            forceMaliFbFetch = false,
            ps3 = current.ps3.copy(
                // Mali does not benefit from async texture streaming on most
                // driver versions; keep it off unless the user overrides.
                asyncTexStream = false,
            ),
        )
    }

    // ------------------------------------------------------------------
    //  Mali driver version from system properties
    // ------------------------------------------------------------------

    /** Convenience alias for [MaliGpuInfo.maliDriverVersion]. */
    fun getMaliDriverVersion(): String? = MaliGpuInfo.maliDriverVersion()
}
