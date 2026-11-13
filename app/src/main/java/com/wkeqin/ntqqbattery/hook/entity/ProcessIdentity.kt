package com.wkeqin.ntqqbattery.hook.entity

/** Compare exact process identities, tolerating trailing /proc cmdline NUL data. */
internal object ProcessIdentity {
    fun isMainProcess(actual: String, main: String): Boolean {
        val actualName = actual.substringBefore('\u0000')
        val mainName = main.substringBefore('\u0000')
        return actualName.isNotEmpty() && mainName.isNotEmpty() && actualName == mainName
    }
}
