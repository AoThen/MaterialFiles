/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ftpserver

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import me.zhanghai.android.files.provider.sftp.client.SecurityProviderHelper
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import org.apache.ftpserver.FtpServerConfigurationException
import org.apache.ftpserver.ssl.SslConfiguration
import org.apache.ftpserver.ssl.SslConfigurationFactory
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

object CertificateGenerator {
    private const val KEYSTORE_FILE_NAME = "ftpserver.p12"
    private const val KEYSTORE_TYPE = "PKCS12"
    private const val KEYSTORE_PASSWORD_PREFERENCE_NAME = "ftp_server_keystore"
    private const val KEYSTORE_PASSWORD_PREFERENCE_KEY = "password"
    private const val KEY_ALIAS = "materialfiles"
    private const val KEY_SIZE = 2048
    private const val NOT_BEFORE_OFFSET_DAYS = 1L
    private const val VALIDITY_DAYS = 365 * 20L
    private const val PASSWORD_BYTE_COUNT = 32

    fun getSslConfiguration(context: Context): SslConfiguration {
        SecurityProviderHelper.init()
        val keystoreFile = File(context.filesDir, KEYSTORE_FILE_NAME)
        val keystorePassword = getOrCreateKeystorePassword(context)
        if (!keystoreFile.exists()) {
            createKeystore(keystoreFile, keystorePassword)
        }
        return try {
            createSslConfiguration(keystoreFile, keystorePassword)
        } catch (e: FtpServerConfigurationException) {
            // Keystore is corrupt, so we regenerate it.
            keystoreFile.delete()
            createKeystore(keystoreFile, keystorePassword)
            createSslConfiguration(keystoreFile, keystorePassword)
        }
    }

    private fun getOrCreateKeystorePassword(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(
            KEYSTORE_PASSWORD_PREFERENCE_NAME, Context.MODE_PRIVATE
        )
        val existingPassword = sharedPreferences.getString(KEYSTORE_PASSWORD_PREFERENCE_KEY, null)
        if (existingPassword != null) {
            return existingPassword
        }
        val passwordBytes = ByteArray(PASSWORD_BYTE_COUNT)
        SecureRandom().nextBytes(passwordBytes)
        val password = Base64.encodeToString(passwordBytes, Base64.NO_WRAP)
        sharedPreferences.edit {
            putString(KEYSTORE_PASSWORD_PREFERENCE_KEY, password)
        }
        return password
    }

    private fun createKeystore(keystoreFile: File, keystorePassword: String) {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(KEY_SIZE)
        val keyPair = keyPairGenerator.generateKeyPair()
        val now = Instant.now()
        val notBefore = Date.from(now.minus(NOT_BEFORE_OFFSET_DAYS, ChronoUnit.DAYS))
        val notAfter = Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS))
        val subject = X500Name("CN=MaterialFiles")
        val serial = BigInteger(160, SecureRandom())
        val certificateHolder = JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.public
        )
            .addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            .addExtension(
                Extension.keyUsage, true,
                KeyUsage(
                    KeyUsage.digitalSignature or KeyUsage.keyEncipherment
                        or KeyUsage.keyCertSign or KeyUsage.cRLSign
                )
            )
            .build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certificateHolder)
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            KEY_ALIAS, keyPair.private, keystorePassword.toCharArray(), arrayOf(certificate)
        )
        keystoreFile.outputStream().use {
            keyStore.store(it, keystorePassword.toCharArray())
        }
    }

    private fun createSslConfiguration(
        keystoreFile: File,
        keystorePassword: String
    ): SslConfiguration = SslConfigurationFactory()
        .apply {
            setKeystoreFile(keystoreFile)
            setKeystoreType(KEYSTORE_TYPE)
            setKeystorePassword(keystorePassword)
            setKeyPassword(keystorePassword)
        }
        .createSslConfiguration()
}
