package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI

/**
 * [Copied](https://github.com/atlassian/infrastructure/blob/release-4.12.0/src/main/kotlin/com/atlassian/performance/tools/infrastructure/api/browser/chromium/Chromium69.kt)
 * and overridden the internal `ChromiumInstaller` with [PatientChromiumInstaller].
 */
class PatientChromium69 : Browser {
    private val chromiumUri = URI("https://www.googleapis.com/download/storage/v1/b/chromium-browser-snapshots/o/Linux_x64%2F576753%2Fchrome-linux.zip?generation=1532051976706023&alt=media")
    private val chromedriverUri = URI("https://s3.eu-central-1.amazonaws.com/jpt-chromedriver/2.43/chromedriver.zip")

    /**
     * Installs chromium 69 with a compatible chromedriver.
     */
    override fun install(ssh: SshConnection) {
        PatientChromiumInstaller(chromiumUri).install(ssh)
        CopiedChromedriverInstaller(chromedriverUri).install(ssh)
    }
}
