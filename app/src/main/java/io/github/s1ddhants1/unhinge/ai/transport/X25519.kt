package io.github.s1ddhants1.unhinge.ai.transport

import java.math.BigInteger
import java.security.SecureRandom

internal object X25519 {
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val TWO: BigInteger = BigInteger.valueOf(2)

    fun generateKeyPair(random: SecureRandom): Pair<ByteArray, ByteArray> {
        val privateKey = ByteArray(32)
        random.nextBytes(privateKey)
        return privateKey to publicKey(privateKey)
    }

    fun publicKey(privateKey: ByteArray): ByteArray {
        val basePoint = ByteArray(32)
        basePoint[0] = 9
        return scalarMultiply(privateKey, basePoint)
    }

    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        if (peerPublicKey.size != 32) throw TlsException("invalid X25519 public key length")
        return scalarMultiply(privateKey, peerPublicKey)
    }

    private fun scalarMultiply(scalar: ByteArray, coordinate: ByteArray): ByteArray {
        if (scalar.size != 32) throw TlsException("invalid X25519 private key length")
        val clamped = scalar.copyOf()
        clamped[0] = (clamped[0].toInt() and 248).toByte()
        clamped[31] = (clamped[31].toInt() and 127).toByte()
        clamped[31] = (clamped[31].toInt() or 64).toByte()
        val coordinateBytes = coordinate.copyOf()
        coordinateBytes[31] = (coordinateBytes[31].toInt() and 127).toByte()
        val x1 = BigInteger(1, coordinateBytes.reversedArray())
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0
        for (bitIndex in 254 downTo 0) {
            val bit = (clamped[bitIndex ushr 3].toInt() ushr (bitIndex and 7)) and 1
            swap = swap xor bit
            if (swap == 1) {
                val temporaryX = x2
                x2 = x3
                x3 = temporaryX
                val temporaryZ = z2
                z2 = z3
                z3 = temporaryZ
            }
            swap = bit
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            x3 = da.add(cb).mod(P).let { it.multiply(it).mod(P) }
            z3 = da.subtract(cb).mod(P).let { x1.multiply(it.multiply(it).mod(P)).mod(P) }
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e).mod(P)).mod(P)).mod(P)
        }
        if (swap == 1) {
            val temporaryX = x2
            x2 = x3
            x3 = temporaryX
        }
        val result = x2.multiply(z2.modPow(P.subtract(TWO), P)).mod(P).toByteArray()
        val fixed = ByteArray(32)
        if (result.size > 32) {
            System.arraycopy(result, result.size - 32, fixed, 0, 32)
        } else {
            System.arraycopy(result, 0, fixed, 32 - result.size, result.size)
        }
        return fixed.reversedArray()
    }
}
