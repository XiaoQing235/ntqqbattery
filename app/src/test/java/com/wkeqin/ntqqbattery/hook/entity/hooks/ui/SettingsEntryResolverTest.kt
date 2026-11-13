package com.wkeqin.ntqqbattery.hook.entity.hooks.ui

import android.content.Context
import org.junit.Assert.*
import org.junit.Test

class SettingsEntryResolverTest {
    class Provider {
        fun a(context: Context): List<String> = emptyList()
        fun b(context: Context): ArrayList<String> = arrayListOf()
        fun unrelated(): List<String> = emptyList()
        fun wrong(context: String): List<String> = emptyList()
    }
    class Item(context: Context, id: Int, title: CharSequence, icon: Int) {
        fun onClick(callback: () -> Unit) {}
    }
    class ItemFive(context: Context, id: Int, title: CharSequence, icon: Int, extra: String?) {
        fun a(callback: () -> Unit) {}
    }
    class NotAnItem(context: Context, id: Int, title: String, icon: Int) {
        fun a(callback: () -> Unit) {}
    }
    class NoClick(context: Context, id: Int, title: CharSequence, icon: Int)

    @Test fun acceptsListSubtypesButNotUnrelatedMethods() {
        assertEquals(setOf("a", "b"), SettingsEntryResolver.providerMethods(Provider::class.java).map { it.name }.toSet())
    }
    @Test fun validatesFourAndFiveArgumentItems() {
        assertTrue(SettingsEntryResolver.supportsProcessor(Item::class.java))
        assertTrue(SettingsEntryResolver.supportsProcessor(ItemFive::class.java))
    }
    @Test fun rejectsWrongConstructorAndMissingClick() {
        assertFalse(SettingsEntryResolver.supportsProcessor(NotAnItem::class.java))
        assertFalse(SettingsEntryResolver.supportsProcessor(NoClick::class.java))
    }
}
