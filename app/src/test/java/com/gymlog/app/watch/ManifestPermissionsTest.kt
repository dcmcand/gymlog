package com.gymlog.app.watch

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Guards the offline/privacy stance: the Pebble integration must not add any INTERNET or
 * Bluetooth permission (PebbleKitAndroid2 brokers through the Core app via IPC). Unit tests
 * run with the module dir (`app/`) as the working directory.
 */
class ManifestPermissionsTest {

    @Test
    fun `manifest declares no internet or bluetooth permission`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(
            "INTERNET permission must not be requested",
            manifest.contains("android.permission.INTERNET"),
        )
        assertFalse(
            "no BLUETOOTH* permission may be requested",
            Regex("android\\.permission\\.BLUETOOTH").containsMatchIn(manifest),
        )
    }
}
