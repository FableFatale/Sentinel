package app.permissionsentinel.data

/**
 * Line codec for the local observation log. One record per line:
 * `1|<epochMs>|<type>|<package>|<extra>`
 * Pure logic — unit tested; the DataStore wrapper lives in [RecordStore].
 */
object RecordCodec {

    const val MAX_LINES = 5000

    fun encode(record: SentinelRecord): String =
        "1|${record.timestamp}|${record.type.id}|${record.packageName}|${record.extra}"

    fun decode(line: String): SentinelRecord? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('|')
        if (parts.size != 5) return null
        if (parts[0] != "1") return null
        val ts = parts[1].toLongOrNull() ?: return null
        val type = RecordType.fromId(parts[2]) ?: return null
        val pkg = parts[3]
        if (pkg.isEmpty()) return null
        val extra = parts[4].toLongOrNull() ?: return null
        return SentinelRecord(ts, type, pkg, extra)
    }

    fun decodeAll(text: String): List<SentinelRecord> =
        text.lineSequence().mapNotNull(::decode).toList()

    fun encodeAll(records: List<SentinelRecord>): String =
        records.joinToString(separator = "\n", postfix = "\n") { encode(it) }

    /** Keeps newest [MAX_LINES] records, sorted by timestamp ascending. */
    fun trim(records: List<SentinelRecord>): List<SentinelRecord> =
        if (records.size <= MAX_LINES) records.sortedBy { it.timestamp }
        else records.sortedBy { it.timestamp }.takeLast(MAX_LINES)
}
