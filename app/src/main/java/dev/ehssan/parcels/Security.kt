package dev.ehssan.parcels

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** The unlock PIN: stored only as a salted PBKDF2 hash, with a growing delay after wrong tries. */
object Pin {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("security", Context.MODE_PRIVATE)

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun isSet(ctx: Context): Boolean = prefs(ctx).contains("hash")

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs(ctx).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt("fails", 0)
            .putLong("until", 0L)
            .apply()
    }

    /** seconds the user must still wait before trying again, 0 if none */
    fun lockoutSeconds(ctx: Context): Int {
        val left = prefs(ctx).getLong("until", 0L) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun verify(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val saltS = p.getString("salt", null) ?: return false
        val hashS = p.getString("hash", null) ?: return false
        val ok = MessageDigest.isEqual(
            hash(pin, Base64.decode(saltS, Base64.NO_WRAP)),
            Base64.decode(hashS, Base64.NO_WRAP)
        )
        if (ok) {
            p.edit().putInt("fails", 0).putLong("until", 0L).apply()
        } else {
            val fails = p.getInt("fails", 0) + 1
            var until = 0L
            if (fails >= 5) {
                val delay = minOf(600L, 30L shl (fails - 5).coerceAtMost(5))
                until = System.currentTimeMillis() + delay * 1000L
            }
            p.edit().putInt("fails", fails).putLong("until", until).apply()
        }
        return ok
    }
}

/** AES-GCM with a key that never leaves the Android Keystore. Used for the hidden-tab list. */
object Crypto {
    private const val ALIAS = "vault_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(data: String): String {
        val parts = data.split(":")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(ct), Charsets.UTF_8)
    }
}

/** Fingerprint / face unlock through the platform BiometricPrompt. */
object Bio {
    fun available(ctx: Context): Boolean {
        val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 30) {
            bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun prompt(activity: Activity, title: String, onOk: () -> Unit, onFail: () -> Unit) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setNegativeButton("استخدم الرمز", activity.mainExecutor) { _, _ -> onFail() }
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onOk()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onFail()
                }
            }
        )
    }
}
