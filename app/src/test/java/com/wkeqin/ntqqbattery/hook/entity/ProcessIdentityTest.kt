package com.wkeqin.ntqqbattery.hook.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessIdentityTest {
    @Test fun exactMainProcessMatches() {
        assertTrue(ProcessIdentity.isMainProcess("com.tencent.mobileqq", "com.tencent.mobileqq"))
    }
    @Test fun nulPaddedMainProcessMatches() {
        assertTrue(ProcessIdentity.isMainProcess("com.tencent.mobileqq\u0000\u0000", "com.tencent.mobileqq"))
    }
    @Test fun subprocessIsNotMainEvenWithNulPadding() {
        assertFalse(ProcessIdentity.isMainProcess("com.tencent.mobileqq:MSF\u0000", "com.tencent.mobileqq"))
        assertFalse(ProcessIdentity.isMainProcess("com.tencent.mobileqq:tool", "com.tencent.mobileqq"))
    }
    @Test fun emptyAndUnrelatedNamesAreRejected() {
        assertFalse(ProcessIdentity.isMainProcess("", ""))
        assertFalse(ProcessIdentity.isMainProcess("\u0000", "com.tencent.mobileqq"))
        assertFalse(ProcessIdentity.isMainProcess("com.tencent.mobileqq.other", "com.tencent.mobileqq"))
    }
}
