package com.cmf.budscontroller.watch

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Wire format documented by CMF-Watch-Pro-2-BLE-Protocol (FFF0 command channel). */
class WatchSession(private val send: (ByteArray) -> Unit, private val onReady: () -> Unit,
                   private val onData: (Int, Int, ByteArray) -> Unit) {
    private var key: ByteArray? = null
    private var authKey: ByteArray? = null
    private var secret: ByteArray? = null
    private var random: ByteArray? = null
    private var stage = 0
    private var input = byteArrayOf()
    private val parts = ByteArrayOutputStream()
    private var partCommand = 0L
    private var expectedPart = 1
    private var expectedCount = 0
    var ready = false
        private set
    fun reset() {
        key?.fill(0); authKey?.fill(0); secret?.fill(0); random?.fill(0)
        key = null; authKey = null; secret = null; random = null
        ready = false; stage = 0; input = byteArrayOf(); parts.reset(); expectedPart = 1
    }
    fun pair(deviceSecret: ByteArray) {
        require(deviceSecret.size == 16)
        reset()
        secret = deviceSecret.copyOf()
        random = ByteArray(16).also { SecureRandom().nextBytes(it) }
        stage = 1
        command(0xffff, 0x8047, random!! + sha(random!! + secret!!), plaintext = true)
    }
    fun command(a: Int, b: Int, payload: ByteArray = byteArrayOf(0xa5.toByte()), plaintext: Boolean = false) {
        require(payload.size <= 8192)
        val chunks = payload.asList().chunked(32).map { it.toByteArray() }.ifEmpty { listOf(byteArrayOf()) }
        chunks.forEachIndexed { index, bytes ->
            val encrypted = !plaintext && key != null
            val body = if (encrypted) crypt(Cipher.ENCRYPT_MODE, bytes + crc(bytes)) else bytes
            val length = if (encrypted) body.size else body.size + 4
            val header = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN).put(0xf5.toByte()).putShort(length.toShort())
                .putShort(a.toShort()).putShort(chunks.size.toShort()).putShort((index+1).toShort()).putShort(b.toShort()).array()
            (header + body).asList().chunked(20).forEach { send(it.toByteArray()) }
        }
    }
    fun receive(bytes: ByteArray) {
        require(input.size + bytes.size <= 65536) { "Watch frame buffer overflow" }
        input += bytes
        while (input.size >= 11) {
            require(input[0] == 0xf5.toByte()) { "Invalid watch frame" }
            val header = ByteBuffer.wrap(input).order(ByteOrder.BIG_ENDIAN)
            header.get()
            val length = header.short.toInt() and 65535
            val a = header.short.toInt() and 65535
            val count = header.short.toInt() and 65535
            val index = header.short.toInt() and 65535
            val b = header.short.toInt() and 65535
            val plaintext = a == 0xffff && b == 0x0048
            val encrypted = key != null && !plaintext
            val actual = if (encrypted) length else length - 4
            require(actual in 0..8192 && count in 1..512 && index in 1..count)
            if (input.size < 11 + actual) return
            val wire = input.copyOfRange(11, 11+actual)
            input = input.copyOfRange(11+actual, input.size)
            val body = if (encrypted) {
                val decoded = crypt(Cipher.DECRYPT_MODE, wire)
                require(decoded.size >= 4)
                val payload = decoded.copyOf(decoded.size-4)
                require(MessageDigest.isEqual(crc(payload), decoded.takeLast(4).toByteArray())) { "Watch CRC mismatch" }
                payload
            } else wire
            val command = (a.toLong() shl 16) or b.toLong()
            if (index == 1) { parts.reset(); expectedPart = 1; expectedCount = count; partCommand = command }
            require(partCommand == command && count == expectedCount && index == expectedPart && parts.size() + body.size <= 65536)
            parts.write(body); expectedPart++
            if (index == count) { val payload = parts.toByteArray(); parts.reset(); handle(a,b,payload) }
        }
    }
    private fun handle(a: Int, b: Int, payload: ByteArray) {
        if (a == 0xffff) when (b) {
            0xa061 -> error("Watch authentication rejected")
            0x0048 -> {
                require(stage == 1 && payload.size == 48)
                val random2 = payload.copyOfRange(0,16)
                require(MessageDigest.isEqual(sha(random2 + secret!!),payload.copyOfRange(16,48))) { "Watch signature mismatch" }
                authKey = sha(random!! + random2 + secret!!).copyOf(16)
                key = authKey!!.copyOf(); stage = 2
                command(0xffff,0x8049,byteArrayOf(0xa5.toByte()) + "CMF Manager".toByteArray())
            }
            0x0049 -> {
                require(stage == 2); stage = 3
                command(0xffff,0x804b)
            }
            0x004c -> {
                require(stage == 3 && payload.isNotEmpty() && payload.size <= 64)
                key = sha(payload + authKey!!).copyOf(16); stage = 4
                command(0xffff,0x804d)
            }
            0x0004 -> if (stage == 4) {
                stage = 5; ready = true
                secret?.fill(0); secret = null; random?.fill(0); random = null
                val now = System.currentTimeMillis()
                val time = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt((now/1000).toInt())
                    .putInt(java.util.TimeZone.getDefault().getOffset(now)).array()
                command(0xffff,0x8004,time)
                onReady()
            }
        }
        if (ready) onData(a,b,payload)
    }
    fun notification(icon: Int, title: String, body: String) {
        check(ready)
        fun truncate(text: String, max: Int): ByteArray {
            var result = ""
            for (point in text.codePoints().toArray()) {
                val next = result + String(Character.toChars(point))
                if (next.toByteArray(Charsets.UTF_8).size > max) break
                result = next
            }
            return result.toByteArray(Charsets.UTF_8)
        }
        val t = truncate(title,20); val content = truncate(body,128)
        command(0x65,1, ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN).put(icon.toByte()).put(0)
            .putInt((System.currentTimeMillis()/1000).toInt()).put(t.size.toByte()).array() + t + content)
    }
    private fun crypt(mode: Int, bytes: ByteArray): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(mode, SecretKeySpec(key!!,"AES"), IvParameterSpec(byteArrayOf(0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,0x60,0x61,0x62,0x63,0x64,0x65,0x66,0x5a)))
        doFinal(bytes)
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun crc(bytes: ByteArray): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(CRC32().apply { update(bytes) }.value.toInt()).array()
}
