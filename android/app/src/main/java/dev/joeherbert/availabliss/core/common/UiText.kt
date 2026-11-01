package dev.joeherbert.availabliss.core.common

/** A one-off message surfaced to the user (snackbar / inline banner). */
data class UserMessage(val text: String, val id: Long = System.nanoTime())
