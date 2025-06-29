package com.android.lbe.util

import android.content.pm.ApplicationInfo
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.android.lbe.common.Constants
import com.android.lbe.sysApp
import com.android.lbe.service.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.*

interface PackageManagerDelegate {
    fun getInstalledPackages(flags: Int): List<PackageInfo>
    fun getApplicationLabel(info: ApplicationInfo): CharSequence
}

class DefaultPackageManagerDelegate(private val packageManager: PackageManager) : PackageManagerDelegate {
    override fun getInstalledPackages(flags: Int): List<PackageInfo> {
        return packageManager.getInstalledPackages(flags)
    }

    override fun getApplicationLabel(info: ApplicationInfo): CharSequence {
        return packageManager.getApplicationLabel(info)
    }
}

object PackageHelper {

    class PackageCache(
        val info: PackageInfo,
        val label: String,
        val icon: Bitmap
    )

    private object Comparators {
        val byLabel = Comparator<String> { o1, o2 ->
            val n1 = loadAppLabel(o1).lowercase(Locale.getDefault())
            val n2 = loadAppLabel(o2).lowercase(Locale.getDefault())
            Collator.getInstance(Locale.getDefault()).compare(n1, n2)
        }
        val byPackageName = Comparator<String> { o1, o2 ->
            val n1 = o1.lowercase(Locale.getDefault())
            val n2 = o2.lowercase(Locale.getDefault())
            Collator.getInstance(Locale.getDefault()).compare(n1, n2)
        }
        val byInstallTime = Comparator<String> { o1, o2 ->
            val n1 = loadPackageInfo(o1).firstInstallTime
            val n2 = loadPackageInfo(o2).firstInstallTime
            n2.compareTo(n1)
        }
        val byUpdateTime = Comparator<String> { o1, o2 ->
            val n1 = loadPackageInfo(o1).lastUpdateTime
            val n2 = loadPackageInfo(o2).lastUpdateTime
            n2.compareTo(n1)
        }
    }

    private lateinit var ipm: IPackageManager
    private lateinit var packageManagerDelegate: PackageManagerDelegate

    private val packageCache = MutableSharedFlow<Map<String, PackageCache>>(replay = 1)
    private val mAppList = MutableSharedFlow<List<String>>(replay = 1)
    val appList: SharedFlow<List<String>> = mAppList

    private val mRefreshing = MutableSharedFlow<Boolean>(replay = 1)
    val isRefreshing: SharedFlow<Boolean> = mRefreshing

    init {
        packageManagerDelegate = DefaultPackageManagerDelegate(sysApp.packageManager)
        invalidateCache()
    }

    fun setPackageManagerDelegate(delegate: PackageManagerDelegate) {
        packageManagerDelegate = delegate
    }

    fun invalidateCache() {
        sysApp.globalScope.launch {
            mRefreshing.emit(true)
            val cache = withContext(Dispatchers.IO) {
                val packages = packageManagerDelegate.getInstalledPackages(0)
                mutableMapOf<String, PackageCache>().also {
                    for (packageInfo in packages) {
                        if (packageInfo.packageName in Constants.packagesShouldNotHide) continue
                        packageInfo.applicationInfo?.let { appInfo ->
                            val label = packageManagerDelegate.getApplicationLabel(appInfo).toString()
                            val icon = sysApp.appIconLoader.loadIcon(appInfo)
                            it[packageInfo.packageName] = PackageCache(packageInfo, label, icon)
                        }
                    }
                }
            }
            packageCache.emit(cache)
            mAppList.emit(cache.keys.toList())
            mRefreshing.emit(false)
        }
    }

    suspend fun sortList(firstComparator: Comparator<String>) {
        var comparator = when (PrefManager.appFilter_sortMethod) {
            PrefManager.SortMethod.BY_LABEL -> Comparators.byLabel
            PrefManager.SortMethod.BY_PACKAGE_NAME -> Comparators.byPackageName
            PrefManager.SortMethod.BY_INSTALL_TIME -> Comparators.byInstallTime
            PrefManager.SortMethod.BY_UPDATE_TIME -> Comparators.byUpdateTime
        }
        if (PrefManager.appFilter_reverseOrder) comparator = comparator.reversed()
        val list = mAppList.first().sortedWith(firstComparator.then(comparator))
        mAppList.emit(list)
    }

    fun loadPackageInfo(packageName: String): PackageInfo = runBlocking {
        packageCache.first()[packageName]!!.info
    }

    fun loadAppLabel(packageName: String): String = runBlocking {
        packageCache.first()[packageName]!!.label
    }

    fun loadAppIcon(packageName: String): Bitmap = runBlocking {
        packageCache.first()[packageName]!!.icon
    }

    fun isSystem(packageName: String): Boolean = runBlocking {
        packageCache.first()[packageName]?.info?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
    }
}
