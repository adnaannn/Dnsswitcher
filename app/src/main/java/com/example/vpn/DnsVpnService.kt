package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ProfileRepository
import com.example.manager.PrivateDnsHelper
import com.example.model.DnsProfile
import com.example.service.DnsTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpnService"
        const val ACTION_START_VPN = "com.example.vpn.START_VPN"
        const val ACTION_STOP_VPN = "com.example.vpn.STOP_VPN"
        const val ACTION_SWITCH_PROFILE = "com.example.vpn.SWITCH_PROFILE"
        const val ACTION_NEXT_PROFILE = "com.example.vpn.NEXT_PROFILE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dns_toggle_vpn_channel"

        const val TUN_INTERFACE_IPV4 = "10.111.222.10"
        const val VIRTUAL_DNS_IP = "10.111.222.1"
        const val VIRTUAL_DNS_IPV4_SUBNET = "10.111.222.0"
        const val VIRTUAL_DNS_IPV4_PREFIX = 24
        const val VIRTUAL_DNS_IPV4_ROUTE_PREFIX = 32

        const val TUN_INTERFACE_IPV6 = "fd00:1:fd00:1::10"
        const val VIRTUAL_DNS_IPV6 = "fd00:1:fd00:1::1"
        const val VIRTUAL_DNS_IPV6_SUBNET = "fd00:1:fd00:1::"
        const val VIRTUAL_DNS_IPV6_PREFIX = 64
        const val VIRTUAL_DNS_IPV6_ROUTE_PREFIX = 64

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _currentProfileFlow = MutableStateFlow<DnsProfile?>(null)
        val currentProfileFlow: StateFlow<DnsProfile?> = _currentProfileFlow.asStateFlow()

        fun startVpn(context: Context, profileId: String? = null) {
            try {
                val intent = Intent(context, DnsVpnService::class.java).apply {
                    action = ACTION_START_VPN
                    profileId?.let { putExtra(EXTRA_PROFILE_ID, it) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN service", e)
            }
        }

        fun stopVpn(context: Context) {
            try {
                val intent = Intent(context, DnsVpnService::class.java).apply {
                    action = ACTION_STOP_VPN
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop VPN service", e)
            }
        }

        fun switchProfile(context: Context, profileId: String) {
            try {
                val intent = Intent(context, DnsVpnService::class.java).apply {
                    action = ACTION_SWITCH_PROFILE
                    putExtra(EXTRA_PROFILE_ID, profileId)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch VPN profile", e)
            }
        }

        fun cycleNext(context: Context) {
            try {
                val intent = Intent(context, DnsVpnService::class.java).apply {
                    action = ACTION_NEXT_PROFILE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cycle next VPN profile", e)
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var dohClient: DohClient? = null
    private val queryExecutor = Executors.newFixedThreadPool(4)

    @Volatile
    private var activeProfile: DnsProfile = DnsProfile.ADGUARD

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        dohClient = DohClient(this)
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_VPN

        when (action) {
            ACTION_STOP_VPN -> {
                stopVpnInternal()
                return START_NOT_STICKY
            }
            ACTION_SWITCH_PROFILE -> {
                val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) {
                    val repository = ProfileRepository.getInstance(this)
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.setActiveProfile(profileId)
                        val profile = repository.getActiveProfile()
                        updateActiveProfileInternal(profile)
                    }
                }
                return START_STICKY
            }
            ACTION_NEXT_PROFILE -> {
                val repository = ProfileRepository.getInstance(this)
                CoroutineScope(Dispatchers.IO).launch {
                    val nextProfile = repository.getNextEnabledProfile()
                    updateActiveProfileInternal(nextProfile)
                }
                return START_STICKY
            }
            ACTION_START_VPN -> {
                // Must call startForeground synchronously to satisfy Android requirements
                promoteToForeground(activeProfile)

                val requestedProfileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                val repository = ProfileRepository.getInstance(this)

                CoroutineScope(Dispatchers.IO).launch {
                    if (requestedProfileId != null) {
                        repository.setActiveProfile(requestedProfileId)
                    }
                    val profile = repository.getActiveProfile()
                    startVpnInternal(profile)
                }
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(profile: DnsProfile) {
        try {
            val notification = buildNotification(profile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground notification", e)
        }
    }

    private fun startVpnInternal(profile: DnsProfile) {
        if (_isRunningFlow.value && vpnInterface != null) {
            updateActiveProfileInternal(profile)
            return
        }

        activeProfile = profile
        _currentProfileFlow.value = profile
        promoteToForeground(profile)

        try {
            val builder = Builder()
                .setSession("DNS Toggle")
                // Dual-stack IPv4 configuration:
                // TUN interface address is assigned TUN_INTERFACE_IPV4 (10.111.222.10/24).
                // System DNS server is set to VIRTUAL_DNS_IP (10.111.222.1), with host route /32 directed to TUN.
                // Having distinct interface and DNS addresses ensures Linux kernel forwards queries to the TUN interface
                // rather than loopback delivery.
                .addAddress(TUN_INTERFACE_IPV4, VIRTUAL_DNS_IPV4_PREFIX)
                .addDnsServer(VIRTUAL_DNS_IP)
                .addRoute(VIRTUAL_DNS_IP, VIRTUAL_DNS_IPV4_ROUTE_PREFIX)
                // Dual-stack IPv6 configuration:
                // TUN interface address is assigned TUN_INTERFACE_IPV6 (fd00:1:fd00:1::10/64).
                // System DNS server is set to VIRTUAL_DNS_IPV6 (fd00:1:fd00:1::1), with subnet route /64 directed to TUN.
                .addAddress(TUN_INTERFACE_IPV6, VIRTUAL_DNS_IPV6_PREFIX)
                .addDnsServer(VIRTUAL_DNS_IPV6)
                .addRoute(VIRTUAL_DNS_IPV6_SUBNET, VIRTUAL_DNS_IPV6_ROUTE_PREFIX)
                .setBlocking(true)

            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not disallow app package from VPN", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            // Allow the system to automatically select and manage the underlying physical network
            builder.setUnderlyingNetworks(null)

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish TUN interface (returned null)")
                stopVpnInternal()
                return
            }

            _isRunningFlow.value = true
            DnsTileService.requestTileUpdate(this)
            PrivateDnsHelper.triggerNetworkRevalidation(this)

            serviceScope = CoroutineScope(Dispatchers.IO + Job())
            serviceScope?.launch {
                runPacketLoop()
            }

            Log.i(TAG, "DNS Toggle VPN established for ${profile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN interface", e)
            stopVpnInternal()
        }
    }

    private fun updateActiveProfileInternal(profile: DnsProfile) {
        activeProfile = profile
        _currentProfileFlow.value = profile
        dohClient?.clearConnectionPool()
        promoteToForeground(profile)
        DnsTileService.requestTileUpdate(this)
        PrivateDnsHelper.triggerNetworkRevalidation(this)
        Log.i(TAG, "Switched active DNS profile to ${profile.name}")
    }

    private fun runPacketLoop() {
        val pfd = vpnInterface ?: return
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(4096)

        try {
            while (serviceScope?.coroutineContext?.get(Job)?.isActive == true) {
                val bytesRead = inputStream.read(packetBuffer)
                if (bytesRead <= 0) break

                val query = DnsPacketParser.parseUdpDnsQuery(packetBuffer, bytesRead)
                if (query != null) {
                    val currentProfileSnapshot = activeProfile
                    queryExecutor.execute {
                        try {
                            val responsePayload = dohClient?.resolve(query.dnsPayload, currentProfileSnapshot)
                            if (responsePayload != null) {
                                val responsePacket = DnsPacketParser.buildUdpDnsResponse(query, responsePayload)
                                synchronized(outputStream) {
                                    outputStream.write(responsePacket)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error processing DNS packet: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Packet loop ended: ${e.message}")
        } finally {
            try {
                inputStream.close()
                outputStream.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun stopVpnInternal() {
        Log.i(TAG, "Stopping DNS Toggle VPN")
        serviceScope?.cancel()
        serviceScope = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null

        _isRunningFlow.value = false
        _currentProfileFlow.value = null
        DnsTileService.requestTileUpdate(this)

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (ignored: Exception) {}

        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        stopVpnInternal()
        queryExecutor.shutdown()
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.w(TAG, "VPN permission revoked by user or system")
        stopVpnInternal()
    }

    private fun buildNotification(profile: DnsProfile): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val switchIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_NEXT_PROFILE
        }
        val switchPendingIntent = PendingIntent.getService(
            this,
            1,
            switchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS Toggle: ${profile.name}")
            .setContentText("Encrypted DNS routing active (${profile.protocol})")
            .setSmallIcon(R.drawable.ic_tile_dns)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_tile_dns, "Next Profile", switchPendingIntent)
            .addAction(R.drawable.ic_tile_dns, "Disconnect", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val desc = getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = desc
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = connectivityManager?.getNetworkCapabilities(network)
                    if (caps != null &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    ) {
                        Log.d(TAG, "Physical underlying network available: $network")
                        try {
                            setUnderlyingNetworks(arrayOf(network))
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not set underlying network", e)
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    ) {
                        try {
                            setUnderlyingNetworks(arrayOf(network))
                        } catch (ignored: Exception) {}
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Underlying network lost: $network")
                    try {
                        val remainingPhysical = connectivityManager?.allNetworks?.firstOrNull { net ->
                            val caps = connectivityManager?.getNetworkCapabilities(net)
                            caps != null &&
                                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        }
                        if (remainingPhysical != null) {
                            setUnderlyingNetworks(arrayOf(remainingPhysical))
                        } else {
                            setUnderlyingNetworks(null)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not reset underlying network", e)
                    }
                }
            }
            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (ignored: Exception) {}
    }
}
