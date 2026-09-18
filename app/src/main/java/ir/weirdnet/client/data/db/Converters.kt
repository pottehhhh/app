package ir.weirdnet.client.data.db

import androidx.room.TypeConverter
import ir.weirdnet.client.data.model.ProtocolType
import ir.weirdnet.client.data.model.SecurityType
import ir.weirdnet.client.data.model.TransportType
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromProtocolType(value: ProtocolType): String = value.name

    @TypeConverter
    fun toProtocolType(value: String): ProtocolType = ProtocolType.valueOf(value)

    @TypeConverter
    fun fromTransportType(value: TransportType): String = value.name

    @TypeConverter
    fun toTransportType(value: String): TransportType = TransportType.valueOf(value)

    @TypeConverter
    fun fromSecurityType(value: SecurityType): String = value.name

    @TypeConverter
    fun toSecurityType(value: String): SecurityType = SecurityType.valueOf(value)

    /** Non-secret transport metadata only -- see [ir.weirdnet.client.data.model.VpnProfile.extra]. */
    @TypeConverter
    fun fromExtraMap(value: Map<String, String>): String {
        val json = JSONObject()
        value.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    @TypeConverter
    fun toExtraMap(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        val json = JSONObject(value)
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
}
