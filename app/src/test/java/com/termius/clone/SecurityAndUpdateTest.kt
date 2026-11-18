package com.termius.clone

import com.termius.clone.data.model.HostEntity
import com.termius.clone.data.model.TerminalThemes
import com.termius.clone.data.security.KeyVaultManager
import com.termius.clone.util.AppUpdateManager
import com.termius.clone.util.ChatImageLoader
import com.termius.clone.util.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 安全加固、版本比对、SSRF 拦截与主题兼容性回归测试。
 */
class SecurityAndUpdateTest {

    @Test
    fun semVerBasicAndPrefixComparison() {
        assertTrue(AppUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.9", "1.1.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.2.3", "V1.2.4"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.24", "v2.0.25"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.2.3", "1.2.3"))
        assertFalse(AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", ""))
        assertTrue(AppUpdateManager.isNewerVersion("", "1.0.0"))
    }

    @Test
    fun semVerPreReleaseAndBuildMetadata() {
        // 正式版高于同核心预发布版
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-beta.1", "1.2.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.2.0", "1.2.0-beta.1"))

        // 核心版本更高时，即使带预发布标签也算更新
        assertTrue(AppUpdateManager.isNewerVersion("1.2.3", "1.2.4-rc1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.2.4-rc1", "1.2.3"))

        // 预发布内部数值与字典序比较
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-alpha.1", "1.2.0-alpha.2"))
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-alpha.2", "1.2.0-alpha.10"))
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-alpha.1", "1.2.0-alpha.beta"))
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-alpha", "1.2.0-alpha.1"))
        assertFalse(AppUpdateManager.isNewerVersion("1.2.0-rc.1", "1.2.0-beta.9"))

        // 构建元数据 (+) 不影响版本优先级
        assertFalse(AppUpdateManager.isNewerVersion("1.2.0+build.1", "1.2.0+build.2"))
        assertTrue(AppUpdateManager.isNewerVersion("1.2.0-rc.1+001", "1.2.0+002"))
    }

    @Test
    fun keyVaultLegacyPlaintextAndCorruptedCiphertext() {
        assertEquals("ENC:v1:", KeyVaultManager.PREFIX)
        assertEquals("", KeyVaultManager.encrypt(""))
        assertEquals("", KeyVaultManager.decrypt(""))

        val alreadyEncrypted = "ENC:v1:alreadyEncryptedPayload"
        assertEquals(alreadyEncrypted, KeyVaultManager.encrypt(alreadyEncrypted))

        // 无前缀存量旧明文原样返回
        assertEquals("legacy_plain_pwd", KeyVaultManager.decrypt("legacy_plain_pwd"))
        assertEquals("legacy_plain_pwd", KeyVaultManager().decrypt("legacy_plain_pwd"))
        assertEquals("", KeyVaultManager(null).encrypt(""))

        // 带前缀但损坏的密文绝不能当作明文返回，必须抛 IllegalStateException
        val corruptedInputs = listOf(
            "ENC:v1:",
            "ENC:v1:AA==",
            "ENC:v1:!!!not-base64!!!"
        )
        for (bad in corruptedInputs) {
            try {
                KeyVaultManager.decrypt(bad)
                fail("Expected IllegalStateException for corrupted ciphertext: $bad")
            } catch (_: IllegalStateException) {
                // expected
            }
            assertEquals("", KeyVaultManager.decryptForUi(bad))
        }
        assertEquals("legacy_plain", KeyVaultManager.decryptForUi("legacy_plain"))
    }

    @Test
    fun hostEntityDefaultThemeAndLegacyAliases() {
        val host = HostEntity(label = "Prod", hostname = "198.51.100.10")
        assertEquals("", host.terminalTheme)

        assertEquals(TerminalThemes.NeonPurple, TerminalThemes.getThemeById("dracula"))
        assertEquals(TerminalThemes.NeonPurple, TerminalThemes.getThemeById("Dracula"))
        assertEquals(TerminalThemes.HackerGreen, TerminalThemes.getThemeById("monokai"))
        assertEquals(TerminalThemes.HackerGreen, TerminalThemes.getThemeById("Monokai"))
        assertEquals(TerminalThemes.ClassicDark, TerminalThemes.getThemeById("non_existent_theme"))
    }

    @Test
    fun chatImageLoaderSsrfBlocksPrivateAndLocalAddresses() {
        val blockedUrls = listOf(
            "http://localhost/img.png",
            "https://localhost.localdomain/img.png",
            "http://127.0.0.1/img.png",
            "http://127.255.0.1:8080/img.png",
            "http://10.0.0.1/img.png",
            "http://172.16.0.1/img.png",
            "http://172.31.255.254/img.png",
            "http://192.168.1.1/img.png",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0/img.png",
            "http://[::1]/img.png",
            "http://[fc00::1]/img.png",
            "http://[fd12:3456:789a::1]/img.png",
            "http://[fe80::1]/img.png",
            "http://[::ffff:127.0.0.1]/img.png",
            "http://127.1/img.png",
            "http://2130706433/img.png",
            "http://0177.0.0.1/img.png",
            "http://0x7f000001/img.png",
            "file:///etc/passwd"
        )
        for (url in blockedUrls) {
            assertFalse("Expected SSRF block for $url", ChatImageLoader.isSafePublicHttpUrlSyntax(url))
        }

        val allowedUrls = listOf(
            "https://example.com/diagram.png",
            "http://172.15.255.254/diagram.png",
            "https://172.32.0.1/diagram.png",
            "https://8.8.8.8/logo.png",
            "https://[2606:4700:4700::1111]/img.webp"
        )
        for (url in allowedUrls) {
            assertTrue("Expected public URL allowed for $url", ChatImageLoader.isSafePublicHttpUrlSyntax(url))
        }
    }

    @Test
    fun imageUtilsHeaderSniffing() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val jpgHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        assertTrue(ImageUtils.looksLikeImageHeader(pngHeader))
        assertTrue(ImageUtils.looksLikeImageHeader(jpgHeader))
        assertFalse(ImageUtils.looksLikeImageHeader(zipHeader))
        assertTrue(ImageUtils.hasImageExtension("screenshot.PNG"))
        assertFalse(ImageUtils.hasImageExtension("notes.txt"))
    }
}
