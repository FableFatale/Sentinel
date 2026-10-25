package app.permissionsentinel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCodecTest {

    @Test
    fun `roundtrip encode decode`() {
        val record = SentinelRecord(
            timestamp = 1_761_000_000_000L,
            type = RecordType.CAMERA_BUSY_ON,
            packageName = "com.tencent.mm",
            extra = 2188L,
        )
        val decoded = RecordCodec.decode(RecordCodec.encode(record))
        assertEquals(record, decoded)
    }

    @Test
    fun `default extra encodes as -1`() {
        val record = SentinelRecord(5L, RecordType.MIC_BUSY_OFF, "com.x")
        assertEquals("1|5|mic_off|com.x|-1", RecordCodec.encode(record))
        assertEquals(record, RecordCodec.decode("1|5|mic_off|com.x|-1"))
    }

    @Test
    fun `malformed lines are skipped`() {
        val text = """
            garbage
            2|100|cam_on|com.x|1
            1|abc|cam_on|com.x|1
            1|100|unknown_type|com.x|1
            1|100|cam_on||1
            1|100|cam_on|com.x|notanumber
            1|100|cam_on|com.x|5
            
        """.trimIndent()
        val records = RecordCodec.decodeAll(text)
        assertEquals(1, records.size)
        assertEquals("com.x", records[0].packageName)
        assertEquals(5L, records[0].extra)
    }

    @Test
    fun `decode null for empty line`() {
        assertNull(RecordCodec.decode(""))
        assertNull(RecordCodec.decode("   "))
    }

    @Test
    fun `trim keeps newest MAX_LINES sorted ascending`() {
        val records = (1..(RecordCodec.MAX_LINES + 100)).map {
            SentinelRecord(it * 1000L, RecordType.SENSITIVE_FG, "pkg$it")
        }.reversed() // newest first on purpose
        val trimmed = RecordCodec.trim(records)
        assertEquals(RecordCodec.MAX_LINES, trimmed.size)
        assertTrue(trimmed.first().timestamp < trimmed.last().timestamp)
        assertEquals(5_100_000L, trimmed.last().timestamp) // newest survives
        assertEquals(101_000L, trimmed.first().timestamp) // oldest 100 dropped
    }

    @Test
    fun `trim sorts small lists too`() {
        val records = listOf(
            SentinelRecord(200L, RecordType.SENSITIVE_FG, "b"),
            SentinelRecord(100L, RecordType.SENSITIVE_FG, "a"),
        )
        val trimmed = RecordCodec.trim(records)
        assertEquals(100L, trimmed.first().timestamp)
    }

    @Test
    fun `encodeAll decodeAll roundtrip preserves all`() {
        val records = listOf(
            SentinelRecord(1L, RecordType.CAMERA_BUSY_ON, "com.a", 10L),
            SentinelRecord(2L, RecordType.CAMERA_BUSY_OFF, "com.a", 20L),
            SentinelRecord(3L, RecordType.MIC_BUSY_ON, "com.b"),
        )
        assertEquals(records, RecordCodec.decodeAll(RecordCodec.encodeAll(records)))
    }
}
