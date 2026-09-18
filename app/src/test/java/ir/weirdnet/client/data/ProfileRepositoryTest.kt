package ir.weirdnet.client.data

import com.google.common.truth.Truth.assertThat
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.VpnProfile
import ir.weirdnet.client.data.repository.ProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProfileRepositoryTest {

    private fun sampleProfile(name: String = "Germany") = VpnProfile(
        name = name,
        protocol = ProtocolType.VLESS,
        server = "de.example.com",
        port = 443,
        secret = "encrypted-placeholder"
    )

    @Test
    fun `duplicate appends a copy suffix and resets session data`() = runTest {
        val dao = FakeProfileDao()
        val repository = ProfileRepository(dao)
        val original = sampleProfile()
        repository.save(original)

        val duplicate = repository.duplicate(original)

        assertThat(duplicate.name).isEqualTo("Germany (copy)")
        assertThat(duplicate.id).isNotEqualTo(original.id)
        assertThat(duplicate.totalBytesDownloaded).isEqualTo(0L)
        assertThat(duplicate.lastConnectedEpochMs).isNull()
    }

    @Test
    fun `duplicate avoids name collisions on repeated duplication`() = runTest {
        val dao = FakeProfileDao()
        val repository = ProfileRepository(dao)
        val original = sampleProfile()
        repository.save(original)

        val firstCopy = repository.duplicate(original)
        val secondCopy = repository.duplicate(original)

        assertThat(firstCopy.name).isEqualTo("Germany (copy)")
        assertThat(secondCopy.name).isEqualTo("Germany (copy 2)")
    }

    @Test
    fun `addSessionStats accumulates rather than overwrites`() = runTest {
        val dao = FakeProfileDao()
        val repository = ProfileRepository(dao)
        val profile = sampleProfile()
        repository.save(profile)

        repository.addSessionStats(profile.id, down = 1000, up = 200)
        repository.addSessionStats(profile.id, down = 500, up = 100)

        val updated = repository.getById(profile.id)!!
        assertThat(updated.totalBytesDownloaded).isEqualTo(1500)
        assertThat(updated.totalBytesUploaded).isEqualTo(300)
    }

    @Test
    fun `isNameTaken ignores the profile being renamed`() = runTest {
        val dao = FakeProfileDao()
        val repository = ProfileRepository(dao)
        val profile = sampleProfile()
        repository.save(profile)

        assertThat(repository.isNameTaken("Germany")).isTrue()
        assertThat(repository.isNameTaken("Germany", excludingId = profile.id)).isFalse()
    }
}
