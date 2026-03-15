package it.atm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun success_getOrNull() {
        val result: AppResult<Int> = AppResult.Success(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun error_getOrNull() {
        val result: AppResult<Int> = AppResult.Error(RuntimeException("fail"))
        assertNull(result.getOrNull())
    }

    @Test
    fun success_getOrThrow() {
        val result: AppResult<String> = AppResult.Success("hello")
        assertEquals("hello", result.getOrThrow())
    }

    @Test(expected = RuntimeException::class)
    fun error_getOrThrow() {
        val result: AppResult<String> = AppResult.Error(RuntimeException("fail"))
        result.getOrThrow()
    }

    @Test
    fun onSuccess_called() {
        var called = false
        AppResult.Success(1).onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun onSuccess_notCalledForError() {
        var called = false
        AppResult.Error(RuntimeException()).onSuccess { called = true }
        assertEquals(false, called)
    }

    @Test
    fun onError_called() {
        var called = false
        AppResult.Error(RuntimeException()).onError { called = true }
        assertTrue(called)
    }

    @Test
    fun runCatching_success() {
        val result = runCatching { 42 }
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun runCatching_failure() {
        val result = runCatching { throw RuntimeException("oops") }
        assertTrue(result is AppResult.Error)
    }
}
