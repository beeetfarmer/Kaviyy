package com.nl.kaviyy.core.data

import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.network.CreateEmailDeviceDto
import com.nl.kaviyy.core.network.EmailDeviceDto
import com.nl.kaviyy.core.network.SendSeriesToEmailDeviceDto
import com.nl.kaviyy.core.network.SendToEmailDeviceDto
import com.nl.kaviyy.core.network.apiCall
import javax.inject.Inject

/** What the server e-mails to a device. */
sealed interface SendTarget {
    data class Chapters(
        val ids: List<Int>,
    ) : SendTarget

    data class Series(
        val id: Int,
    ) : SendTarget
}

/** Send to Device (Kavita e-mails files to Kindle/Kobo/…). Online only, nothing cached. */
interface DeviceRepository {
    suspend fun devices(server: Server): AppResult<List<EmailDeviceDto>>

    /** Creates the device when [EmailDeviceDto.id] is 0, else updates it. */
    suspend fun save(
        server: Server,
        device: EmailDeviceDto,
    ): AppResult<EmailDeviceDto>

    suspend fun delete(
        server: Server,
        id: Int,
    ): AppResult<Unit>

    suspend fun send(
        server: Server,
        deviceId: Int,
        target: SendTarget,
    ): AppResult<Unit>
}

/** A plausible e-mail address: one @ with text on both sides and a dot in the domain. The server validates too. */
fun isDeviceEmail(value: String): Boolean {
    val local = value.substringBefore('@', "")
    val domain = value.substringAfter('@', "")
    return local.isNotEmpty() && '@' !in domain && '.' in domain && !domain.startsWith('.') && !domain.endsWith('.') &&
        value.none { it.isWhitespace() }
}

class KavitaDeviceRepository
    @Inject
    constructor(
        private val apis: ServerApis,
    ) : DeviceRepository {
        override suspend fun devices(server: Server) = apiCall { apis.api(server).devices() }

        override suspend fun save(
            server: Server,
            device: EmailDeviceDto,
        ) = apiCall {
            val api = apis.api(server)
            if (device.id == 0) {
                api.createDevice(
                    CreateEmailDeviceDto(device.name.orEmpty(), device.platform, device.emailAddress.orEmpty()),
                )
            } else {
                api.updateDevice(device)
            }
        }

        override suspend fun delete(
            server: Server,
            id: Int,
        ) = apiCall { apis.api(server).deleteDevice(id) }

        override suspend fun send(
            server: Server,
            deviceId: Int,
            target: SendTarget,
        ) = apiCall {
            when (target) {
                is SendTarget.Chapters -> {
                    apis.api(server).sendToDevice(SendToEmailDeviceDto(deviceId, target.ids))
                }

                is SendTarget.Series -> {
                    apis
                        .api(
                            server,
                        ).sendSeriesToDevice(SendSeriesToEmailDeviceDto(deviceId, target.id))
                }
            }
        }
    }
