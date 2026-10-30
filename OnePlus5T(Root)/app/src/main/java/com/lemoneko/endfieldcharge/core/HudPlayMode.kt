package com.lemoneko.endfieldcharge.core

/** Which of the two `zmd-charge` timelines to play. */
enum class HudPlayMode {
    /** Full three state animation: bolt pops in, pill expands, ripples, title, then numbers. */
    CHARGE,

    /** Simplified animation: just the pill with the numbers, no bolt-first, no ripples. */
    UNPLUG,
}
