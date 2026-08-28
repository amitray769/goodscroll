package com.amitray.goodscroll.reminder

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wall-clock source. Injected rather than calling [System.currentTimeMillis] inline so reminder
 * delays can be asserted against a fixed "now" in unit tests.
 */
fun interface TimeProvider {
    fun nowMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
