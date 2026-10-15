package com.vynce.app.utils.listentogether

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredRoom(
    val roomCode: String,
    val hostName: String,
    val hostIp: String,
    val port: Int,
    val lastDiscoveredAt: Long = System.currentTimeMillis()
)

object NsdRoomDiscovery {
    private const val TAG = "NsdRoomDiscovery"
    private const val SERVICE_TYPE = "_vyncelisten._tcp."

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val discoveredMap = ConcurrentHashMap<String, DiscoveredRoom>()
    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val discoveredRooms = _discoveredRooms.asStateFlow()

    @Synchronized
    fun registerRoomService(context: Context, roomCode: String, hostName: String, port: Int) {
        unregisterRoomService()

        try {
            val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "Vynce_${roomCode}"
                serviceType = SERVICE_TYPE
                setPort(port)
                setAttribute("code", roomCode)
                setAttribute("host", hostName)
            }

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(service: NsdServiceInfo) {
                    Log.i(TAG, "NSD service registered: ${service.serviceName} on port $port")
                }

                override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "NSD registration failed: $errorCode")
                }

                override fun onServiceUnregistered(service: NsdServiceInfo) {
                    Log.i(TAG, "NSD service unregistered")
                }

                override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "NSD unregistration failed: $errorCode")
                }
            }

            registrationListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD room service", e)
        }
    }

    @Synchronized
    fun unregisterRoomService() {
        try {
            val manager = nsdManager
            val listener = registrationListener
            if (manager != null && listener != null) {
                manager.unregisterService(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NSD room service", e)
        } finally {
            registrationListener = null
        }
    }

    @Synchronized
    fun startDiscovery(context: Context) {
        stopDiscovery()
        discoveredMap.clear()
        _discoveredRooms.value = emptyList()

        try {
            val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager

            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "Discovery start failed: $errorCode")
                    runCatching { manager.stopServiceDiscovery(this) }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "Discovery stop failed: $errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    Log.i(TAG, "Service discovery started for $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.i(TAG, "Service discovery stopped")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType == SERVICE_TYPE || serviceInfo.serviceType.contains("vyncelisten")) {
                        try {
                            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                                    Log.w(TAG, "Resolve failed for ${service.serviceName}: $errorCode")
                                }

                                override fun onServiceResolved(service: NsdServiceInfo) {
                                    val host = service.host?.hostAddress ?: return
                                    val port = service.port
                                    val rawName = service.serviceName
                                    val codeFromAttr = service.attributes?.get("code")?.let { String(it) }
                                    val hostFromAttr = service.attributes?.get("host")?.let { String(it) }

                                    val roomCode = codeFromAttr ?: rawName.substringAfter("Vynce_", "").ifBlank {
                                        EmbeddedRoomServer.ipToRoomCode(host)
                                    }
                                    val hostName = hostFromAttr ?: "Vynce Room"

                                    val room = DiscoveredRoom(
                                        roomCode = roomCode,
                                        hostName = hostName,
                                        hostIp = host,
                                        port = port
                                    )
                                    discoveredMap[roomCode] = room
                                    _discoveredRooms.value = discoveredMap.values.toList()
                                    Log.i(TAG, "Discovered room: $roomCode ($hostName at $host:$port)")
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Error resolving NSD service", e)
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val code = serviceInfo.serviceName.substringAfter("Vynce_", "")
                    if (code.isNotBlank()) {
                        discoveredMap.remove(code)
                        _discoveredRooms.value = discoveredMap.values.toList()
                    }
                }
            }

            discoveryListener = listener
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD discovery", e)
        }
    }

    @Synchronized
    fun stopDiscovery() {
        try {
            val manager = nsdManager
            val listener = discoveryListener
            if (manager != null && listener != null) {
                manager.stopServiceDiscovery(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping NSD discovery", e)
        } finally {
            discoveryListener = null
            discoveredMap.clear()
            _discoveredRooms.value = emptyList()
        }
    }
}
