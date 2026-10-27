package com.example

data class NaamJapState(
    val currentBead: Int = 0,
    val completedMalas: Int = 0,
    val targetMalaGoal: Int = 16,
    val lifetimeChants: Long = 0L,
    val isVoiceActive: Boolean = false,
    val lastChantedWord: String = "",
    val names: List<String> = emptyList(),
    val targetName: String = "",
    val isIncrementAnimating: Boolean = false,
    val vibrateOnChant: Boolean = false,
    val isBackgroundActive: Boolean = false,
    val statusText: String = "Add your holy name & chant"
)
