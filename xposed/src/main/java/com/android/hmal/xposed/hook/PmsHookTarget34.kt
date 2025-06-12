package com.android.hmal.xposed.hook

import android.os.Binder
import android.os.Build
import androidx.annotation.RequiresApi
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import com.android.hmal.common.Constants
import com.android.hmal.xposed.*
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.Q) // SDK29+
class PmsHookTarget34Compat(private val service: HMALService) : IFrameworkHook {

    companion object {
        private const val TAG = "PHT34"
    }

    private val getPackagesForUidMethod by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            findMethod("com.android.server.pm.Computer") { name == "getPackagesForUid" }
        } else {
            findMethod("com.android.server.pm.PackageManagerService") { name == "getPackagesForUid" }
        }
    }

    private var hook: XC_MethodHook.Unhook? = null
    private var exphook: XC_MethodHook.Unhook? = null
    private var lastFilteredApp: AtomicReference<String?> = AtomicReference(null)

    override fun load() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hook = findMethod("com.android.server.pm.AppsFilterImpl", findSuper = true) {
                name == "shouldFilterApplication"
            }.hookBefore { param ->
                runCatching {
                    val snapshot = param.args[0]
                    val callingUid = param.args[1] as Int
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val callingApps = Utils.binderLocalScope {
                        runCatching {
                            getPackagesForUidMethod.invoke(snapshot, callingUid) as? Array<String>
                        }.getOrNull()
                    } ?: return@hookBefore

                    val targetApp = Utils.getPackageNameFromPackageSettings(param.args[3])

                    for (caller in callingApps) {
                        if (service.shouldHide(caller, targetApp)) {
                            param.result = true
                            val last = lastFilteredApp.getAndSet(caller)
                            if (last != caller) return@hookBefore
                        }
                    }
                }.onFailure { unload() }
            }

            exphook = findMethodOrNull("com.android.server.pm.PackageManagerService", findSuper = true) {
                name == "getArchivedPackageInternal"
            }?.hookBefore { param ->
                runCatching {
                    val callingUid = Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val callingApps = Utils.binderLocalScope {
                        runCatching {
                            service.pms.getPackagesForUid(callingUid)
                        }.getOrNull()
                    } ?: return@hookBefore

                    val targetApp = param.args[0].toString()

                    for (caller in callingApps) {
                        if (service.shouldHide(caller, targetApp)) {
                            param.result = null
                            val last = lastFilteredApp.getAndSet(caller)
                            if (last != caller) return@hookBefore
                        }
                    }
                }.onFailure { unload() }
            }

        } else { // 适配 SDK 29 ~ 33
            hook = findMethod("com.android.server.pm.PackageManagerService", findSuper = true) {
                name == "getPackageInfo"
            }.hookBefore { param ->
                runCatching {
                    val callingUid = Binder.getCallingUid()
                    if (callingUid == Constants.UID_SYSTEM) return@hookBefore

                    val callingApps = Utils.binderLocalScope {
                        runCatching {
                            getPackagesForUidMethod.invoke(param.thisObject, callingUid) as? Array<String>
                        }.getOrNull()
                    } ?: return@hookBefore

                    val targetApp = param.args[0].toString()

                    for (caller in callingApps) {
                        if (service.shouldHide(caller, targetApp)) {
                            param.result = null
                            val last = lastFilteredApp.getAndSet(caller)
                            if (last != caller) return@hookBefore
                        }
                    }
                }.onFailure { unload() }
            }
        }
    }

    override fun unload() {
        hook?.unhook()
        hook = null
        exphook?.unhook()
        exphook = null
    }
}
