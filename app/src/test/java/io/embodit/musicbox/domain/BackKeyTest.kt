package io.embodit.musicbox.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** QA #4 q4-1 (D60): BACK never exits; it lets go of what is held, reopens a closed window, else does nothing. */
class BackKeyTest {
    @Test fun holdingLetsGoFirst() {
        assertEquals(BackKey.Act.RELEASE_HELD, BackKey.decide(holding = true, panelOpen = true))
        assertEquals(BackKey.Act.RELEASE_HELD, BackKey.decide(holding = true, panelOpen = false))
    }

    @Test fun closedWindowReopens() = assertEquals(BackKey.Act.OPEN_WINDOW, BackKey.decide(holding = false, panelOpen = false))

    @Test fun openWindowNothing() = assertEquals(BackKey.Act.NOTHING, BackKey.decide(holding = false, panelOpen = true))

    @Test fun threePressesNeverLeaveTheApp() {
        // press B three times from each starting point: every outcome is one of the three harmless acts
        for (h in listOf(true, false)) for (p in listOf(true, false)) {
            var holding = h; var open = p
            repeat(3) {
                when (BackKey.decide(holding, open)) {
                    BackKey.Act.RELEASE_HELD -> holding = false
                    BackKey.Act.OPEN_WINDOW -> open = true
                    BackKey.Act.NOTHING -> Unit
                }
            }
            assertEquals(false, holding); assertEquals(true, open)
        }
    }
}
