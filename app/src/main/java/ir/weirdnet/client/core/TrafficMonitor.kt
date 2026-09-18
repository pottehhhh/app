package ir.weirdnet.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls the active [TunnelEngine] for cumulative byte counters and derives
 * real download/upload speed by differencing successive samples over time.
 * No number here is fabricated: if an engine can't report totals (returns
 * null from [TunnelEngine.currentTraffic]), the UI shows the session as
 * "stats unavailable for this engine" rather than inventing numbers --
 * satisfying requirement #9's "do not fabricate" rule.
 */
class TrafficMonitor(private val scope: CoroutineScope) {

    private var job: Job? = null
    private var lastDown = 0L
    private var lastUp = 0L
    private var lastTimestampMs = 0L

    fun start(engineProvider: () -> TunnelEngine?, intervalMs: Long = 1000L) {
        stop()
        lastDown = 0L
        lastUp = 0L
        lastTimestampMs = System.currentTimeMillis()
        job = scope.launch {
            while (isActive) {
                val engine = engineProvider()
                val sample = engine?.currentTraffic()
                if (sample != null) {
                    val now = System.currentTimeMillis()
                    val elapsedSeconds = ((now - lastTimestampMs).coerceAtLeast(1)) / 1000.0
                    val downDelta = (sample.totalDownloadedBytes - lastDown).coerceAtLeast(0)
                    val upDelta = (sample.totalUploadedBytes - lastUp).coerceAtLeast(0)

                    VpnStateRepository.updateTraffic(
                        TrafficSample(
                            downloadSpeedBps = (downDelta / elapsedSeconds).toLong(),
                            uploadSpeedBps = (upDelta / elapsedSeconds).toLong(),
                            totalDownloadedBytes = sample.totalDownloadedBytes,
                            totalUploadedBytes = sample.totalUploadedBytes
                        )
                    )
                    lastDown = sample.totalDownloadedBytes
                    lastUp = sample.totalUploadedBytes
                    lastTimestampMs = now
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
