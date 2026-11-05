package com.zq.topnews.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mock auth flow is local, so these checks cover the parts the UI relies on:
 * validation messages, the built-in demo account, session persistence and the
 * fact that registered passwords are never written to the store in plain text.
 */
class MockAuthRepositoryTest {

    private val store = InMemoryAuthStore()
    private val repository = MockAuthRepository(store)

    @Test
    fun demoAccountSignsInAndRestoresAfterRestart() {
        val result = repository.login(DemoAccount.ACCOUNT, DemoAccount.PASSWORD)
        assertTrue(result is AuthResult.Success)
        assertEquals(DemoAccount.NICKNAME, repository.currentUser.value?.nickname)

        val restarted = MockAuthRepository(store)
        assertEquals(DemoAccount.ACCOUNT, restarted.currentUser.value?.account)
        assertEquals(DemoAccount.NICKNAME, restarted.currentUser.value?.nickname)
    }

    @Test
    fun wrongPasswordAndUnknownAccountAreRejected() {
        val wrongPassword = repository.login(DemoAccount.ACCOUNT, "654321")
        assertTrue(wrongPassword is AuthResult.Failure)
        assertEquals("账号或密码错误", (wrongPassword as AuthResult.Failure).message)

        val unknownAccount = repository.login("nobody", "123456")
        assertTrue(unknownAccount is AuthResult.Failure)
        assertNull(repository.currentUser.value)
    }

    @Test
    fun registrationValidatesEveryField() {
        assertEquals(
            "请输入昵称",
            (repository.register("", "13800138000", "123456", "123456") as AuthResult.Failure).message,
        )
        assertEquals(
            "昵称需为 2-16 个字符",
            (repository.register("a", "13800138000", "123456", "123456") as AuthResult.Failure).message,
        )
        assertEquals(
            "请输入 3-30 位手机号或邮箱",
            (repository.register("小明", "a b", "123456", "123456") as AuthResult.Failure).message,
        )
        assertEquals(
            "密码需为 6-32 位字符",
            (repository.register("小明", "13800138000", "123", "123") as AuthResult.Failure).message,
        )
        assertEquals(
            "两次输入的密码不一致",
            (repository.register("小明", "13800138000", "123456", "654321") as AuthResult.Failure).message,
        )
        assertNull(repository.currentUser.value)
    }

    @Test
    fun registrationSignsInAndPersistsAccountWithoutPlainPassword() {
        val result = repository.register("小明", "13800138000", "secret123", "secret123")
        assertTrue(result is AuthResult.Success)
        assertEquals("小明", repository.currentUser.value?.nickname)

        val raw = store.value.orEmpty()
        assertTrue(raw.contains("13800138000"))
        assertFalse(raw.contains("secret123"))

        val restarted = MockAuthRepository(store)
        assertEquals("小明", restarted.currentUser.value?.nickname)
        restarted.logout()
        assertNull(restarted.currentUser.value)

        val login = restarted.login(" 13800138000 ", "secret123")
        assertTrue(login is AuthResult.Success)
        assertEquals("小明", (login as AuthResult.Success).user.nickname)

        val wrongPassword = restarted.login("13800138000", "not-the-password")
        assertTrue(wrongPassword is AuthResult.Failure)
    }

    @Test
    fun duplicateAccountIsRejected() {
        repository.register("小明", "13800138000", "secret123", "secret123")
        repository.logout()

        val duplicate = repository.register("小红", "13800138000", "secret123", "secret123")
        assertTrue(duplicate is AuthResult.Failure)
        assertEquals(
            "该账号已注册，请直接登录",
            (duplicate as AuthResult.Failure).message,
        )
    }

    @Test
    fun demoAccountCannotBeRegistered() {
        val result = repository.register("体验官", DemoAccount.ACCOUNT, "secret123", "secret123")
        assertTrue(result is AuthResult.Failure)
        assertEquals(
            "该账号为演示账号，请使用其他账号",
            (result as AuthResult.Failure).message,
        )
    }
}
