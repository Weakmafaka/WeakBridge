package dev.weakbridge.ble

import java.util.UUID

interface GattTransport {
    suspend fun subscribe(
        service: UUID,
        characteristic: UUID,
        enabled: Boolean = true,
        indication: Boolean = false,
    )

    suspend fun write(service: UUID, characteristic: UUID, value: ByteArray)

    suspend fun read(service: UUID, characteristic: UUID): ByteArray

    fun has(service: UUID, characteristic: UUID): Boolean
}
