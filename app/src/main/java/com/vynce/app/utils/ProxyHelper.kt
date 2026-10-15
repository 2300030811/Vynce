/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.vynce.app.constants.ProxyEnabledKey
import com.vynce.app.constants.ProxyTypeKey
import com.vynce.app.constants.ProxyUrlKey
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

object ProxyHelper {
    fun createProxy(enabled: Boolean, type: Proxy.Type, url: String): Proxy? {
        if (!enabled || url.isBlank() || url == "host:port" || !url.contains(":")) {
            return null
        }
        return try {
            val parts = url.trim().split(":")
            val host = parts[0].trim()
            val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            if (host.isBlank()) return null
            Proxy(type, InetSocketAddress(host, port))
        } catch (_: Exception) {
            null
        }
    }

    fun getProxy(dataStore: DataStore<Preferences>): Proxy? {
        val enabled = dataStore.get(ProxyEnabledKey, false)
        if (!enabled) return null
        val typeStr = dataStore.get(ProxyTypeKey, "HTTP")
        val type = if (typeStr.equals("SOCKS", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val url = dataStore.get(ProxyUrlKey, "")
        return createProxy(enabled, type, url)
    }

    fun applyProxy(builder: OkHttpClient.Builder, proxy: Proxy?): OkHttpClient.Builder {
        if (proxy != null) {
            builder.proxy(proxy)
        }
        return builder
    }
}
