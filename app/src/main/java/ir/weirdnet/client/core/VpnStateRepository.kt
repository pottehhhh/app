package ir.weirdnet.client.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide singleton exposing the VPN connection state and live traffic to the UI.
 * [WeirdNetVpnService] is the only writer; everything else (ViewModels, widgets)
 * only reads. Kept as a plain singleton (no DI framework) so the data flow stays
 * easy to follow: Service owns the truth, UI observes it.
 */
object VpnStateRepository {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSample())
    val traffic: StateFlow<TrafficSample> = _traffic.asStateFlow()

    fun update(state: ConnectionState) {
        _connectionState.value = state
    }

    fun updateTraffic(sample: TrafficSample) {
        _traffic.value = sample
    }

    fun reset() {
        _connectionState.value = ConnectionState.Disconnected
        _traffic.value = TrafficSample()
    }
}
