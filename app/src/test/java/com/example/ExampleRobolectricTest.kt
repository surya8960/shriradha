package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Radha Reminder", appName)
    }

    @Test
    fun testVoiceReminderStatusDefault() {
        val status = VoiceReminderStatus()
        assertEquals(ReminderPhase.IDLE, status.phase)
        assertEquals(0, status.cycleCount)
        assertEquals(0, status.recognizedCount)
        assertEquals(15, status.secondsRemaining)
        assertEquals("Stopped (Idle)", status.phaseDisplayName)
    }
}
