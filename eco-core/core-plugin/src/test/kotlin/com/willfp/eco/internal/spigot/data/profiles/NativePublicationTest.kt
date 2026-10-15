package com.willfp.eco.internal.spigot.data.profiles
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.internal.spigot.data.profiles.impl.EcoProfile
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
class NativePublicationTest {
    @Test fun `commit publication preserves decimal values without reading or writing a handler`() {
        val handler = mockk<ProfileHandler>()
        val profile = object : EcoProfile(UUID.randomUUID(),handler) {}
        val level = mockk<PersistentDataKey<Int>>(); every { level.defaultValue } returns 0
        val xp = mockk<PersistentDataKey<Double>>(); every { xp.defaultValue } returns 0.0
        val keys = setOf<PersistentDataKey<*>>(level,xp)
        profile.publishCommittedValues(keys,mapOf(level to 100,xp to 100.02))
        assertEquals(100,profile.read(level));assertEquals(100.02,profile.read(xp))
        verify { handler wasNot Called }
        assertThrows(IllegalArgumentException::class.java) { profile.publishCommittedValues(keys,mapOf(level to "wrong-type",xp to 999.0)) }
        assertEquals(100.02,profile.read(xp));verify { handler wasNot Called }
    }
    @Test fun `failed admission cannot change the cached native value`() {
        val handler = mockk<ProfileHandler>();val writer = mockk<ProfileWriter>()
        val key = mockk<PersistentDataKey<Int>>(); every { key.defaultValue } returns 0
        val profile = object : EcoProfile(UUID.randomUUID(),handler) {}
        profile.publishCommittedValues(setOf(key),mapOf(key to 100))
        every { handler.profileWriter } returns writer
        every { writer.prepareWrite(any(),key,101) } throws IllegalStateException("Fenced")
        assertThrows(IllegalStateException::class.java) { profile.write(key,101) }
        assertEquals(100,profile.read(key))
        verify(exactly=0) { writer.writePrepared(any(),any<PersistentDataKey<Int>>(),any()) }
    }
}
