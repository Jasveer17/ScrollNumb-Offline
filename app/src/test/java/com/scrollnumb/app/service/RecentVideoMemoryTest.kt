package com.scrollnumb.app.service

import com.scrollnumb.app.core.util.Constants
import org.junit.Assert.*
import org.junit.Test

class RecentVideoMemoryTest {

    @Test
    fun `getVideoTwoAgo returns null when less than three videos recorded`() {
        val memory = RecentVideoMemory()
        assertNull(memory.getVideoTwoAgo())

        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_creator1_video_one")
        assertNull(memory.getVideoTwoAgo())

        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_creator2_video_two")
        assertNull(memory.getVideoTwoAgo())
    }

    @Test
    fun `getVideoTwoAgo returns video watched two videos ago`() {
        val memory = RecentVideoMemory()

        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_mkbhd_camera_shootout")
        memory.recordVideo(Constants.PLATFORM_INSTAGRAM, "ig_natgeo_polar_bears")
        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_veritasium_quantum_levitation")

        // Currently watching video 3 (veritasium)
        // 1 video ago = natgeo
        // 2 videos ago = mkbhd
        val twoAgo = memory.getVideoTwoAgo()
        assertNotNull(twoAgo)
        assertEquals("mkbhd", twoAgo?.author)
        assertEquals("camera shootout", twoAgo?.title)
        assertTrue(twoAgo?.displayLabel?.contains("@mkbhd") == true)

        // Add 4th video
        memory.recordVideo(Constants.PLATFORM_INSTAGRAM, "ig_cheflife_pasta_recipe")
        // Currently watching video 4 (cheflife)
        // 1 video ago = veritasium
        // 2 videos ago = natgeo
        val twoAgoUpdated = memory.getVideoTwoAgo()
        assertNotNull(twoAgoUpdated)
        assertEquals("natgeo", twoAgoUpdated?.author)
        assertEquals("polar bears", twoAgoUpdated?.title)
    }

    @Test
    fun `duplicate consecutive signatures are not double recorded`() {
        val memory = RecentVideoMemory()

        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_channel1_title1")
        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_channel1_title1") // Duplicate loop
        assertEquals(1, memory.size())

        memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_channel2_title2")
        assertEquals(2, memory.size())
    }

    @Test
    fun `capacity is bounded to specified limit`() {
        val memory = RecentVideoMemory(capacity = 5)
        for (i in 1..8) {
            memory.recordVideo(Constants.PLATFORM_YOUTUBE, "ch_chan${i}_vid${i}")
        }
        assertEquals(5, memory.size())
    }
}
