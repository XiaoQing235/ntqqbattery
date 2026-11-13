package com.wkeqin.ntqqbattery.hook.entity.hooks.ui

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method

/**
 * Resolve QQ settings by shape as well as name. QQ 9.3 obfuscates its provider.
 * Matching strategy reference: TSBattery QQTIMHooker (b1f5612); this resolver is
 * independently implemented and retains NTQQBattery's own page and storage.
 * Resolution is once per main-process lifetime; no persistent negative cache.
 */
internal object SettingsEntryResolver {
    data class Targets(val providers: List<Method>, val processor: Class<*>?)

    private val providerNames = listOf(
        "com.tencent.mobileqq.setting.main.MainSettingConfigProvider",
        "com.tencent.mobileqq.setting.main.NewSettingConfigProvider",
        "com.tencent.mobileqq.setting.main.b"
    )
    private val processorNames = listOf(
        "com.tencent.mobileqq.setting.processor.SimpleItemProcessor",
        "com.tencent.mobileqq.setting.processor.g",
        "com.tencent.mobileqq.setting.processor.h",
        "com.tencent.mobileqq.setting.processor.i",
        "com.tencent.mobileqq.setting.processor.j",
        "as3.i"
    )

    internal fun providerMethods(type: Class<*>): List<Method> = type.declaredMethods.filter {
        List::class.java.isAssignableFrom(it.returnType) &&
            it.parameterTypes.contentEquals(arrayOf(Context::class.java))
    }

    internal fun clickMethods(type: Class<*>): List<Method> = type.declaredMethods.filter {
        it.returnType == Void.TYPE && it.parameterTypes.size == 1 &&
            it.parameterTypes[0].name == "kotlin.jvm.functions.Function0"
    }.sortedBy { it.name }

    internal fun supportsProcessor(type: Class<*>): Boolean {
        val prefix = arrayOf(Context::class.java, Int::class.javaPrimitiveType,
            CharSequence::class.java, Int::class.javaPrimitiveType)
        return clickMethods(type).isNotEmpty() && type.declaredConstructors.any {
            val args = it.parameterTypes
            (args.size == 4 || args.size == 5 && args[4] == String::class.java) &&
                args.take(4).toTypedArray().contentEquals(prefix)
        }
    }

    fun resolve(loader: ClassLoader): Targets {
        fun load(name: String): Class<*>? = runCatching { Class.forName(name, false, loader) }.getOrNull()
        val providers = linkedSetOf<Class<*>>()
        providerNames.mapNotNullTo(providers, ::load)
        val processors = linkedSetOf<Class<*>>()
        val returnedProcessors = linkedSetOf<Class<*>>()
        processorNames.mapNotNullTo(processors, ::load)

        fun collectReturnTypes() {
            providers.forEach { provider ->
                provider.declaredMethods.filter {
                    it.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                        it.returnType.name.startsWith("com.tencent.mobileqq.setting.processor.")
                }.forEach { processors.add(it.returnType); returnedProcessors.add(it.returnType) }
            }
        }
        collectReturnTypes()
        // A known provider can coexist with the renamed provider actually used by
        // the new screen. Scan even if a legacy class remains in the APK.
        runCatching {
            System.loadLibrary("dexkit")
            DexKitBridge.create(loader, false).use { dex ->
                dex.findClass {
                    matcher { usingStrings("140134.140135", "功能", "隐私") }
                }.forEach { match -> load(match.name)?.let(providers::add) }
                collectReturnTypes()
                run {
                    dex.findClass {
                        searchPackages("com.tencent.mobileqq.setting.processor")
                        matcher {
                            methods {
                                add {
                                    name = "<init>"
                                    paramTypes("android.content.Context", "int", "java.lang.CharSequence", "int")
                                }
                                add {
                                    paramTypes("kotlin.jvm.functions.Function0")
                                    returnType = "void"
                                }
                            }
                        }
                    }.forEach { match -> load(match.name)?.let(processors::add) }
                    dex.findMethod {
                        matcher { usingStrings("SimpleItemProcessor") }
                    }.forEach { match ->
                        runCatching { match.getMethodInstance(loader).declaringClass }
                            .getOrNull()?.let(processors::add)
                    }
                }
            }
        }.onFailure { YLog.error("QuickEntryHook: DexKit resolution failed, retaining named candidates: ${it.message}") }
        val valid = processors.filter { runCatching { supportsProcessor(it) }.getOrDefault(false) }
        val abstractType = listOf(
            "com.tencent.mobileqq.setting.main.processor.AccountSecurityItemProcessor",
            "com.tencent.mobileqq.setting.main.processor.AboutItemProcessor"
        ).firstNotNullOfOrNull { load(it)?.superclass }
        val processor = valid.firstOrNull { it in returnedProcessors && (abstractType == null || it.superclass == abstractType) }
            ?: valid.filter { abstractType != null && it.superclass == abstractType }.singleOrNull()
            ?: valid.singleOrNull()
        if (processor == null && valid.size > 1) {
            YLog.error("QuickEntryHook: ambiguous item processors: ${valid.joinToString { it.name }}")
        }
        val methods = providers.flatMap { runCatching { providerMethods(it) }.getOrDefault(emptyList()) }.distinct()
        return Targets(methods, processor)
    }
}
