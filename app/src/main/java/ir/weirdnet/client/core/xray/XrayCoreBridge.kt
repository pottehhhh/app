package ir.weirdnet.client.core.xray

import ir.weirdnet.client.util.WeirdLogger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Bridge to `libv2ray.*`, the Kotlin/Java package gomobile generates from
 * `2dust/AndroidLibXrayLite`'s Go source when you add `libv2ray.aar` to
 * `app/libs/` (see docs/XRAY_INTEGRATION.md). This file is the ONLY place in
 * WEIRDNET that touches that generated API, and it does so via reflection
 * rather than a direct, statically-typed reference.
 *
 * WHY REFLECTION, NOT A DIRECT IMPORT: `libv2ray.aar` is a large, optional,
 * binary-distributed dependency you add yourself, not a normal Maven
 * artifact Gradle can always resolve (see docs/XRAY_INTEGRATION.md for why).
 * A direct `import libv2ray.CoreController` would make this ONE missing file
 * break compilation of the ENTIRE app module -- including WireGuard, the
 * parsers, the UI, and every existing unit test -- since Gradle/Kotlin
 * compile a module as a single unit; there is no way to "optionally compile"
 * one file within it. Reflection confines that dependency to exactly this
 * file: everything else keeps building and testing normally whether or not
 * the AAR is present, and once it IS present, every call below reaches the
 * exact same real Xray-core entrypoints a direct reference would -- this is
 * not a mock or a fallback, it is the real API, invoked dynamically.
 *
 * The API targeted was confirmed by reading the current upstream source
 * (`libv2ray_main.go`) directly, not guessed:
 *
 * ```go
 * type CoreCallbackHandler interface {
 *     Startup() int
 *     Shutdown() int
 *     OnEmitStatus(int, string) int
 * }
 * type CoreController struct { ... }
 * func NewCoreController(s CoreCallbackHandler) *CoreController
 * func (x *CoreController) StartLoop(configContent string, tunFd int32) (err error)
 * func (x *CoreController) StopLoop() error
 * func InitCoreEnv(envPath string, key string)
 * ```
 *
 * gomobile's standard binding convention maps Go package `libv2ray` to a
 * Java/Kotlin class `libv2ray.Libv2ray` holding top-level functions as
 * static methods, `Go func Foo(...)` -> `Java Class.foo(...)` (camelCase).
 * If a specific downloaded version's generated names differ slightly (this
 * has happened across releases), [start] throws a clear, logged
 * [NoSuchMethodException] identifying exactly which lookup failed --
 * update the method/class name strings below to match your version; nothing
 * else in the app needs to change either way.
 */
internal class XrayCoreBridge(private val assetDir: String) {

    private var controllerInstance: Any? = null
    private var stopLoopMethod: Method? = null

    /**
     * True only if libv2ray.aar's classes are actually present AND loadable
     * right now. Catches [Throwable], not just [ClassNotFoundException]:
     * a present-but-corrupted or ABI-mismatched AAR can throw
     * [LinkageError]/`NoClassDefFoundError` during class loading, and this
     * probe must degrade to "not available" rather than crash the caller --
     * [XrayEngine.availability] has nothing else wrapping this call.
     */
    val isAvailable: Boolean
        get() = try {
            Class.forName(LIBV2RAY_FACADE_CLASS)
            true
        } catch (e: Throwable) {
            false
        }

    /**
     * Starts Xray-core and blocks (up to [readyTimeoutMs]) until it has
     * actually signaled readiness via its own `Startup()` callback, rather
     * than returning as soon as `startLoop` itself returns.
     *
     * This matters: Go's `StartLoop` hands off to internal goroutines that
     * bind the SOCKS inbound socket asynchronously, so returning immediately
     * would create a real race -- [XrayEngine] starts hev-socks5-tunnel
     * right after this call returns, and if that inbound socket isn't bound
     * and listening yet, hev's very first connection attempt to it could
     * fail. Using the core's own readiness callback as the synchronization
     * signal (instead of guessing with a fixed delay) makes this
     * deterministic.
     */
    fun start(configJson: String, tunFd: Int, readyTimeoutMs: Long = 5000L, onStatus: (code: Int, message: String) -> Unit) {
        val facadeClass = Class.forName(LIBV2RAY_FACADE_CLASS)
        val callbackInterface = Class.forName(CALLBACK_HANDLER_INTERFACE)
        val controllerClass = Class.forName(CORE_CONTROLLER_CLASS)

        try {
            val initCoreEnv = facadeClass.getMethod("initCoreEnv", String::class.java, String::class.java)
            invokeStatic(initCoreEnv, assetDir, "")
        } catch (e: NoSuchMethodException) {
            // Not present on every AndroidLibXrayLite version; WEIRDNET's
            // generated config uses no geosite/geoip routing rules, so this
            // call being unavailable is not fatal -- see XrayEngine's doc
            // comment on why geoip.dat/geosite.dat aren't required here.
            WeirdLogger.w("XrayCoreBridge", "initCoreEnv not found on this AndroidLibXrayLite version; continuing without it")
        }

        val readySignal = CountDownLatch(1)
        val callbackProxy = Proxy.newProxyInstance(
            callbackInterface.classLoader,
            arrayOf(callbackInterface),
            CallbackInvocationHandler(onStatus, readySignal)
        )

        val newCoreController = facadeClass.getMethod("newCoreController", callbackInterface)
        val controller = invokeStatic(newCoreController, callbackProxy)
            ?: throw IllegalStateException("newCoreController returned null")

        val startLoop = controllerClass.getMethod("startLoop", String::class.java, Int::class.javaPrimitiveType)
        invokeInstance(startLoop, controller, configJson, tunFd)

        controllerInstance = controller
        stopLoopMethod = controllerClass.getMethod("stopLoop")

        val becameReady = readySignal.await(readyTimeoutMs, TimeUnit.MILLISECONDS)
        if (!becameReady) {
            // Not necessarily fatal -- some AndroidLibXrayLite versions may
            // not invoke Startup() at all for a successful start (the
            // interface's exact call contract isn't guaranteed identical
            // across every release). Logged rather than thrown so a working
            // core on such a version isn't rejected on a technicality; the
            // small residual race this leaves is far better than the
            // unconditional immediate-return this replaces.
            WeirdLogger.w("XrayCoreBridge", "Xray core did not signal Startup() within ${readyTimeoutMs}ms; continuing anyway")
        }
    }

    fun stop() {
        try {
            controllerInstance?.let { instance -> stopLoopMethod?.let { invokeInstance(it, instance) } }
        } catch (e: Exception) {
            WeirdLogger.e("XrayCoreBridge", "Error invoking stopLoop via reflection", e)
        }
        controllerInstance = null
        stopLoopMethod = null
    }

    /** Unwraps InvocationTargetException so callers see the real underlying cause. */
    private fun invokeStatic(method: Method, vararg args: Any?): Any? = try {
        method.invoke(null, *args)
    } catch (e: InvocationTargetException) {
        throw (e.targetException as? Exception) ?: e
    }

    private fun invokeInstance(method: Method, target: Any, vararg args: Any?): Any? = try {
        method.invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw (e.targetException as? Exception) ?: e
    }

    private class CallbackInvocationHandler(
        private val onStatus: (code: Int, message: String) -> Unit,
        private val readySignal: CountDownLatch
    ) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any {
            return when (method.name) {
                "startup" -> {
                    WeirdLogger.i("XrayCoreBridge", "Xray core startup callback")
                    readySignal.countDown()
                    0
                }
                "shutdown" -> {
                    WeirdLogger.i("XrayCoreBridge", "Xray core shutdown callback")
                    0
                }
                "onEmitStatus" -> {
                    val code = (args?.getOrNull(0) as? Int) ?: 0
                    val message = (args?.getOrNull(1) as? String).orEmpty()
                    onStatus(code, WeirdLogger.redact(message))
                    0
                }
                // equals/hashCode/toString from Object, called on the proxy itself
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "XrayCoreBridge.CallbackProxy"
                else -> 0
            }
        }
    }

    companion object {
        private const val LIBV2RAY_FACADE_CLASS = "libv2ray.Libv2ray"
        private const val CALLBACK_HANDLER_INTERFACE = "libv2ray.CoreCallbackHandler"
        private const val CORE_CONTROLLER_CLASS = "libv2ray.CoreController"
    }
}
