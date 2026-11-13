package com.wkeqin.ntqqbattery.hook.entity.hooks.ui

import android.app.Activity
import android.content.Context
import com.wkeqin.ntqqbattery.hook.entity.HookPlan
import com.wkeqin.ntqqbattery.hook.entity.HookStage
import com.wkeqin.ntqqbattery.hook.entity.ProcessScope
import com.wkeqin.ntqqbattery.hook.factory.openModuleSettings
import com.wkeqin.ntqqbattery.ui.activity.BaseHostActivity
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.*
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object QuickEntryHook : YukiBaseHooker() {

    private const val ENTRY_ID = 0x4E545142
    private const val ENTRY_TITLE = "NTQQBattery"
    private var hostLoader: ClassLoader? = null
    private val injectedGroups = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Any, Boolean>())

    val plan = HookPlan(
        id = "quick-entry-app-create",
        stage = HookStage.APP_CREATE,
        processScope = ProcessScope.MAIN_ONLY
    ) {
        hostLoader = it?.classLoader
        registerHostActivityProxy(it)
        loadHooker(QuickEntryHook)
    }

    override fun onHook() {
        hookSettingsProviders()
    }

    private fun registerHostActivityProxy(context: Context?) {
        val hostContext = context ?: return
        runCatching {
            hostContext.registerModuleAppActivities(proxy = BaseHostActivity.FALLBACK_PROXY_ACTIVITY)
        }.onFailure {
            YLog.error("QuickEntryHook: failed to register module activity proxy: ${it.message}")
        }
    }

    private fun hookSettingsProviders() {
        runCatching {
            val loader = hostLoader ?: error("QuickEntryHook: host class loader unavailable")
            val targets = SettingsEntryResolver.resolve(loader)
            if (targets.providers.isEmpty()) {
                error("QuickEntryHook: no settings provider matched; QQ version may be unsupported")
            }
            val processor = targets.processor
                ?: error("QuickEntryHook: no compatible item processor matched")
            val listeners = SettingsEntryResolver.clickMethods(processor)
            val callback = createProviderHook(processor, listeners)
            var installed = 0
            targets.providers.forEach { method ->
                runCatching { XposedBridge.hookMethod(method, callback) }
                    .onSuccess { installed++ }
                    .onFailure { YLog.error("QuickEntryHook: cannot hook ${method.declaringClass.name}.${method.name}: ${it.message}") }
            }
            YLog.info("QuickEntryHook: installed $installed/${targets.providers.size} providers; processor=${processor.name}")
        }.onFailure {
            YLog.error("QuickEntryHook provider hook failed: ${it.message}")
        }
    }

    private fun createProviderHook(
        simpleItemProcessor: Class<*>,
        setClickListenerMethods: List<Method>
    ): XC_MethodHook {
        val constructorInfo = findSimpleItemProcessorConstructor(simpleItemProcessor)

        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val original = param.result as? List<*> ?: return
                val result = original.toMutableList()
                val context = param.args.firstOrNull() as? Context ?: return
                if (containsEntry(result)) return

                runCatching {
                    val entryItem = createEntryItem(
                        context = context,
                        constructor = constructorInfo.first,
                        constructorArgCount = constructorInfo.second,
                        setClickListenerMethods = setClickListenerMethods
                    )

                    val group = createItemGroup(result, entryItem)
                    val providerClassName = param.method.declaringClass.name
                    val indexToInsert = if (providerClassName.contains("NewSettingConfigProvider") || providerClassName == "com.tencent.mobileqq.setting.main.b") 2 else 1
                    injectedGroups[group] = true
                    result.add(indexToInsert.coerceAtMost(result.size), group)
                    param.result = result
                }.onFailure {
                    YLog.error("QuickEntryHook inject failed: ${it.message}")
                }
            }
        }
    }

    private fun findSimpleItemProcessorConstructor(simpleItemProcessor: Class<*>): Pair<Constructor<*>, Int> {
        return runCatching {
            simpleItemProcessor.getDeclaredConstructor(
                Context::class.java,
                Int::class.javaPrimitiveType,
                CharSequence::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            ) to 5
        }.getOrElse {
            simpleItemProcessor.getDeclaredConstructor(
                Context::class.java,
                Int::class.javaPrimitiveType,
                CharSequence::class.java,
                Int::class.javaPrimitiveType
            ) to 4
        }
    }

    private fun createEntryItem(
        context: Context,
        constructor: Constructor<*>,
        constructorArgCount: Int,
        setClickListenerMethods: List<Method>
    ): Any {
        val iconResId = findSettingsIconResId(context)
        constructor.isAccessible = true
        val entryItem = if (constructorArgCount == 5) {
            constructor.newInstance(context, ENTRY_ID, ENTRY_TITLE, iconResId, null)
        } else {
            constructor.newInstance(context, ENTRY_ID, ENTRY_TITLE, iconResId)
        }

        val function0Class = setClickListenerMethods.first().parameterTypes[0]
        val unitInstance = function0Class.classLoader
            ?.loadClass("kotlin.Unit")
            ?.getField("INSTANCE")
            ?.get(null)

        val clickCallback = Proxy.newProxyInstance(
            function0Class.classLoader,
            arrayOf(function0Class)
        ) { _, method, _ ->
            if (method.name == "invoke") {
                runCatching {
                    val activity = context as? Activity
                        ?: error("QuickEntryHook: context is not an Activity: ${context.javaClass.name}")
                    activity.openModuleSettings()
                }
                    .onFailure { YLog.error("QuickEntryHook click failed: ${it.message}") }
                unitInstance
            } else {
                null
            }
        }

        setClickListenerMethods.forEach { method ->
            runCatching {
                method.isAccessible = true
                method.invoke(entryItem, clickCallback)
            }.onFailure {
                YLog.error("QuickEntryHook: failed to bind ${method.name}: ${it.message}")
            }
        }
        return entryItem
    }

    private fun createItemGroup(result: MutableList<Any?>, entryItem: Any): Any {
        val groupClass = result.firstOrNull()?.javaClass
            ?: error("QuickEntryHook: empty setting groups")
        val list = arrayListOf(entryItem)

        return runCatching {
            groupClass.getDeclaredConstructor(List::class.java, CharSequence::class.java, CharSequence::class.java)
                .apply { isAccessible = true }
                .newInstance(list, "", "")
        }.getOrElse {
            val defaultMarkerClass = "kotlin.jvm.internal.DefaultConstructorMarker".toClassOrNull()
                ?: error("QuickEntryHook: DefaultConstructorMarker not found")
            groupClass.getDeclaredConstructor(
                List::class.java,
                CharSequence::class.java,
                CharSequence::class.java,
                Int::class.javaPrimitiveType,
                defaultMarkerClass
            ).apply { isAccessible = true }.newInstance(list, "", "", 6, null)
        }
    }

    private fun containsEntry(groups: List<Any?>): Boolean {
        return groups.any { group ->
            if (group != null && injectedGroups.containsKey(group)) return@any true
            runCatching {
                group?.javaClass?.declaredFields
                    ?.onEach { it.isAccessible = true }
                    ?.mapNotNull { it.get(group) }
                    ?.any { value -> value.toString().contains(ENTRY_TITLE, ignoreCase = true) }
                    ?: false
            }.getOrDefault(false)
        }
    }

    private fun findSettingsIconResId(context: Context): Int {
        return listOf("qui_tuning", "ic_setting_me", "qq_setting_me_item_nor")
            .asSequence()
            .map { context.resources.getIdentifier(it, "drawable", context.packageName) }
            .firstOrNull { it != 0 }
            ?: 0
    }
}
