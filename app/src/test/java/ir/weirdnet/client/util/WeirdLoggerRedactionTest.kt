package ir.weirdnet.client.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeirdLoggerRedactionTest {

    @Test
    fun `redacts a uuid`() {
        val redacted = WeirdLogger.redact("Connecting with id 123e4567-e89b-12d3-a456-426614174000")
        assertThat(redacted).doesNotContain("123e4567-e89b-12d3-a456-426614174000")
        assertThat(redacted).contains("«uuid-redacted»")
    }

    @Test
    fun `redacts userinfo inside a uri`() {
        val redacted = WeirdLogger.redact("Parsing vless://123e4567-e89b-12d3-a456-426614174000@example.com:443")
        assertThat(redacted).doesNotContain("123e4567")
    }

    @Test
    fun `redacts password-style query params`() {
        val redacted = WeirdLogger.redact("Config had password=SuperSecret123&other=fine")
        assertThat(redacted).contains("password=«redacted»")
        assertThat(redacted).contains("other=fine")
    }

    @Test
    fun `redacts wireguard-style base64 keys`() {
        val redacted = WeirdLogger.redact("Using key 6PYIWklQtESVQKLxeUwUKAmgOP+Fs5tBk3Wp5EGL9nY=")
        assertThat(redacted).doesNotContain("6PYIWklQtESVQKLxeUwUKAmgOP+Fs5tBk3Wp5EGL9nY=")
    }

    @Test
    fun `leaves ordinary messages untouched`() {
        val message = "Tunnel established in 340ms"
        assertThat(WeirdLogger.redact(message)).isEqualTo(message)
    }
}
