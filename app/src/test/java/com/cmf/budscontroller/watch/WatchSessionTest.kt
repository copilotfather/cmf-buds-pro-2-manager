package com.cmf.budscontroller.watch

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.CRC32

class WatchSessionTest {
    private val secret = ByteArray(16) { it.toByte() }
    private fun frame(a: Int,b: Int,bytes: ByteArray, encrypted: Boolean = false): ByteArray =
        ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN).put(0xf5.toByte()).putShort((bytes.size + if(encrypted) 0 else 4).toShort())
            .putShort(a.toShort()).putShort(1).putShort(1).putShort(b.toShort()).array()+bytes
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private val iv = byteArrayOf(0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,0x60,0x61,0x62,0x63,0x64,0x65,0x66,0x5a)
    private fun encrypted(key: ByteArray, payload: ByteArray): ByteArray {
        val crc = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(CRC32().apply { update(payload) }.value.toInt()).array()
        return Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(this@WatchSessionTest.iv)); doFinal(payload+crc)
        }
    }
    @Test fun fullAuthenticationAndFragmentedReplies() {
        val sent = mutableListOf<ByteArray>()
        var ready = false
        val session = WatchSession({ sent.add(it) }, { ready = true }, { _,_,_ -> })
        session.pair(secret)
        // Recover only the generated nonce from the two plaintext request chunks.
        val wire = sent.fold(byteArrayOf()) { a,b -> a+b }
        val random1 = wire.copyOfRange(11,27)
        val random2 = ByteArray(16) { (it+20).toByte() }
        val reply = frame(0xffff,0x48,random2+sha(random2+secret))
        reply.asList().chunked(7).forEach { session.receive(it.toByteArray()) }
        val auth = sha(random1+random2+secret).copyOf(16)
        session.receive(frame(0xffff,0x49,encrypted(auth,byteArrayOf(1)),true))
        val nonce = ByteArray(16) { (it+40).toByte() }
        session.receive(frame(0xffff,0x4c,encrypted(auth,nonce),true))
        val key = sha(nonce+auth).copyOf(16)
        session.receive(frame(0xffff,4,encrypted(key,byteArrayOf(0xa5.toByte())),true))
        assertTrue(ready)
        assertTrue(session.ready)
        session.notification(255,"Привет 👋 длинный заголовок","Тест уведомления")
        session.reset()
        assertFalse(session.ready)
    }
    @Test fun rejectsInvalidPairingSignature() {
        val session = WatchSession({}, {}, { _,_,_ -> })
        session.pair(secret)
        assertThrows(IllegalArgumentException::class.java) { session.receive(frame(0xffff,0x48,ByteArray(48))) }
        assertFalse(session.ready)
    }
    @Test fun rejectsCorruptFrameHeader() {
        val session = WatchSession({}, {}, { _,_,_ -> })
        assertThrows(IllegalArgumentException::class.java) { session.receive(ByteArray(11)) }
    }
    @Test fun cannotSendNotificationBeforeAuthentication() {
        val session = WatchSession({}, {}, { _,_,_ -> })
        assertThrows(IllegalStateException::class.java) { session.notification(255,"Title","Body") }
    }
}
