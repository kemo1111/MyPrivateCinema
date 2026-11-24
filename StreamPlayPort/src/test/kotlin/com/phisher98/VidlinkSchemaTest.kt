package com.phisher98

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class VidlinkSchemaTest {
    private val gson = Gson()

    @Test
    fun oldPlaylistShapeStillParses() {
        val r = gson.fromJson("""{"stream":{"playlist":"https://h.example/a.m3u8"}}""", VidlinkResponse::class.java)
        assertEquals("https://h.example/a.m3u8", r.stream.playlist)
        assertNull(r.stream.qualities)
    }

    @Test
    fun newQualitiesShapeParsesWithoutPlaylist() {
        val json = """{"stream":{"type":"file","qualities":{
            "720":{"type":"mp4","url":"https://c.example/a.mp4","requiresProxy":true},
            "1080":{"type":"mp4","url":"https://c.example/b.mp4","requiresProxy":false}}}}"""
        val r = gson.fromJson(json, VidlinkResponse::class.java)
        assertNull(r.stream.playlist)
        val q = r.stream.qualities!!
        assertEquals(setOf("720", "1080"), q.keys)
        assertEquals(true, q["720"]!!.requiresProxy)
        assertEquals(false, q["1080"]!!.requiresProxy)
        assertEquals("https://c.example/b.mp4", q["1080"]!!.url)
    }
}
