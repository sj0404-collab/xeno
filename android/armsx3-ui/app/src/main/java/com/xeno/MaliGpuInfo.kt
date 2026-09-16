package com.xeno

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20

/**
 * Detects Mali GPU model from the system GL_RENDERER string and maps it to a
 * performance tier with recommended emulator settings. Mali GPUs are used in
 * MediaTek Dimensity/Hexagon SoCs and Samsung Exynos devices — custom driver
 * packs do not exist for them, so there is no Turnip-style recommendation, but
 * there are significant per-tier tuning differences.
 *
 * Result is cached after the first probe, exactly like [GpuInfo].
 */
object MaliGpuInfo {

    enum class Tier { LOW, MID, HIGH, ULTRA }

    data class MaliGpuProfile(
        val model: String,
        val tier: Tier,
        /** Recommended internal resolution scale as a percentage (100 = native). */
        val recommendedUpscale: Int,
        /** Recommended SPU thread count. */
        val recommendedSpuThreads: Int,
        /** Recommended shader mode (matches Ps3Settings.shaderMode enum). */
        val recommendedShaderMode: Int,
        val asyncComputeSupported: Boolean,
        /** VRAM allocation limit in MB for this tier. */
        val recommendedVramLimitMb: Int,
        /** Human-readable description of the tier / GPU. */
        val description: String,
    )

    @Volatile private var cachedProfile: MaliGpuProfile? = null
    @Volatile private var probed = false

    /** Profile for the system Mali GPU, or null if none detected. Cached. */
    fun profile(): MaliGpuProfile? {
        if (probed) return cachedProfile
        synchronized(this) {
            if (probed) return cachedProfile
            val renderer = probeRenderer()
            cachedProfile = renderer?.let { profileForRenderer(it) }
            probed = true
            return cachedProfile
        }
    }

    /** True when the system GPU is a Mali. Cached. */
    fun isMali(): Boolean = profile() != null

    /** Preset profiles for each Mali tier, usable for pre-seeding / diagnostics. */
    fun presetFor(tier: Tier): MaliGpuProfile? = presetProfiles[tier]

    private val presetProfiles: Map<Tier, MaliGpuProfile> = mapOf(
        Tier.LOW to MaliGpuProfile(
            model = "Mali-G5x",
            tier = Tier.LOW,
            recommendedUpscale = 75,
            recommendedSpuThreads = 2,
            recommendedShaderMode = 0,   // Legacy recompiler only: no async on entry GPUs
            asyncComputeSupported = false,
            recommendedVramLimitMb = 1024,
            description = "MaliLow preset.",
        ),
        Tier.MID to MaliGpuProfile(
            model = "Mali-G7x",
            tier = Tier.MID,
            recommendedUpscale = 100,
            recommendedSpuThreads = 4,
            recommendedShaderMode = 0,   // Legacy recompiler only: G7x async is unreliable
            asyncComputeSupported = false,
            recommendedVramLimitMb = 1536,
            description = "MaliMid preset.",
        ),
        Tier.HIGH to MaliGpuProfile(
            model = "Mali-G710+",
            tier = Tier.HIGH,
            recommendedUpscale = 150,
            recommendedSpuThreads = 6,
            recommendedShaderMode = 1,
            asyncComputeSupported = true,
            recommendedVramLimitMb = 2048,
            description = "MaliHigh preset.",
        ),
        Tier.ULTRA to MaliGpuProfile(
            model = "Mali-G925+",
            tier = Tier.ULTRA,
            recommendedUpscale = 200,
            recommendedSpuThreads = 6,
            recommendedShaderMode = 1,
            asyncComputeSupported = true,
            recommendedVramLimitMb = 3072,
            description = "MaliUltra preset.",
        ),
    )

    // ------------------------------------------------------------------
    //  GL_RENDERER probe (identical technique to GpuInfo.probe)
    // ------------------------------------------------------------------

    private fun probeRenderer(): String? {
        // Quick heuristic: skip the EGL probe on Qualcomm / other SoCs where Mali
        // is never the primary GPU, saving an offscreen context. A device still
        // probes when the manufacturer is Samsung, the hardware/board smells like
        // MediaTek/Exynos, or ro.hardware.egl names Mali.
        val hardware = android.os.Build.HARDWARE?.lowercase() ?: ""
        val board = android.os.Build.BOARD?.lowercase() ?: ""
        val eglProp = getprop("ro.hardware.egl")?.lowercase() ?: ""
        val likelyMali =
            android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
                hardware.contains("mt") || hardware.contains("mediatek") ||
                hardware.contains("mali") || hardware.contains("samsungexynos") ||
                board.contains("mt") || board.contains("mali") || board.contains("exynos") ||
                eglProp.contains("mali")
        if (!likelyMali) return null
        return runCatching { eglProbe() }.getOrNull()?.takeIf { it.contains("Mali", ignoreCase = true) }
    }

    private fun eglProbe(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return null
        try {
            val cfgAttrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, cfgAttrs, 0, cfgs, 0, 1, num, 0) || num[0] == 0) return null
            val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val ctx = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) return null
            val pbAttrs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surf = EGL14.eglCreatePbufferSurface(display, cfgs[0], pbAttrs, 0)
            try {
                if (!EGL14.eglMakeCurrent(display, surf, surf, ctx)) return null
                return GLES20.glGetString(GLES20.GL_RENDERER)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surf)
                EGL14.eglDestroyContext(display, ctx)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    // ------------------------------------------------------------------
    //  Model extraction + tier mapping
    // ------------------------------------------------------------------

    /** Extract the Mali model name (e.g. "Mali-G715") from a GL_RENDERER string.
     *  Only matches G-series (Bifrost / Valhall / after); T-series (Midgard) and
     *  older are ignored — they do not appear in modern GL_RENDERER strings and
     *  have no Mali-G tier mapping. */
    private fun extractModel(renderer: String): String? {
        return Regex("""Mali-G(\d+\w*)""", RegexOption.IGNORE_CASE)
            .find(renderer)
            ?.let { match -> "Mali-G${match.groupValues[1].uppercase()}" }
    }

    /** Numeric part of the model for tier comparison (e.g. "G715" -> 715). */
    private fun modelNumber(model: String): Int? {
        return Regex("""\d+""").find(model.substringAfter("Mali-", ""))?.value?.toIntOrNull()
    }

    /**
     * Tier by model number. Two-digit G7x (71/76/78) are Bifrost-class mid GPUs;
     * three-digit G7xx (710+, e.g. G710/G715/G720) are the Valhall big cores, and
     * G925+ is flagship. G5x/G3x and low G6xx fall to LOW. This ordering is why
     * magnitude alone is not enough: 78 < 710 but a G78 outclasses a G52.
     */
    private fun tierForNumber(num: Int): Tier = when {
        num >= 925 -> Tier.ULTRA   // Mali-G925+
        num >= 710 -> Tier.HIGH    // Mali-G710, G715, G720
        num >= 68  -> Tier.MID     // Mali-G71, G76, G78 (Bifrost; also G68/G77)
        else       -> Tier.LOW     // Mali-G51, G52, G31, G57, low G6xx
    }

    private fun profileForRenderer(renderer: String): MaliGpuProfile? {
        val model = extractModel(renderer) ?: return null
        val num = modelNumber(model) ?: return null
        val base = presetProfiles[tierForNumber(num)] ?: return null
        return base.copy(
            model = model,
            description = when (base.tier) {
                Tier.LOW -> "Mali-G5x/G31 — entry-level; lower resolution and fewer SPU threads."
                Tier.MID -> "Mali-G7x (pre-G710) — capable at native res with moderate SPU count."
                Tier.HIGH -> "Mali-G710/G715/G720 — strong; upscale beyond native with full SPU count."
                Tier.ULTRA -> "Mali-G925+ — flagship class; high resolution with all SPU threads."
            },
        )
    }

    // ------------------------------------------------------------------
    //  Mali driver version from system properties
    // ------------------------------------------------------------------

    /** Best-effort Mali driver version from Android system properties. */
    fun maliDriverVersion(): String? {
        val egl = getprop("ro.hardware.egl")?.lowercase()
        if (egl != null && !egl.contains("mali")) return null
        // ro.board.platform carries the SoC platform string (e.g. "mt6983")
        val platform = getprop("ro.board.platform")
        val glVersion = runCatching {
            GLES20.glGetString(GLES20.GL_VERSION)
        }.getOrNull()
        return when {
            glVersion != null -> glVersion
            platform != null -> platform
            else -> null
        }
    }

    private fun getprop(key: String): String? = try {
        val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
        val result = proc.inputStream.bufferedReader().readLine()?.trim()
        proc.destroy()
        result?.takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }
}
