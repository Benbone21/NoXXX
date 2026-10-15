package com.noxxx.guard.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretTest {
    private val stored = Secret.hash("trust1234")

    @Test fun rightPasswordVerifies() = assertTrue(Secret.verify("trust1234", stored))

    @Test fun wrongPasswordFails() = assertFalse(Secret.verify("trust12345", stored))

    @Test fun emptyPasswordFailsWithoutThrowing() = assertFalse(Secret.verify("", stored))

    @Test fun garbageStoredValueFails() = assertFalse(Secret.verify("trust1234", "not-a-hash"))
}
