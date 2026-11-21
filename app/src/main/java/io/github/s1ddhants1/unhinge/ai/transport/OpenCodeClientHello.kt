package io.github.s1ddhants1.unhinge.ai.transport

internal object OpenCodeClientHello {
    private val CIPHER_SUITES = intArrayOf(
        0x1301, 0x1302, 0x1303, 0xc02b, 0xc02f, 0xc02c, 0xc030, 0xcca9, 0xcca8,
        0xc009, 0xc013, 0xc00a, 0xc014, 0x009c, 0x009d, 0x002f, 0x0035,
    )

    private val SIGNATURE_ALGORITHMS = intArrayOf(
        0x0403, 0x0804, 0x0401, 0x0503, 0x0805, 0x0501, 0x0806, 0x0601, 0x0807, 0x0603, 0x0201,
    )

    private const val GROUP_X25519 = 0x001d
    private const val GROUP_SECP256R1 = 0x0017
    private const val GROUP_SECP384R1 = 0x0018

    fun build(
        host: String,
        x25519PublicKey: ByteArray,
        random: ByteArray,
        sessionId: ByteArray,
    ): ByteArray {
        val extensions = TlsWriter()
            .extension(0x0000) {
                vec16 {
                    u8(0)
                    vec16 { raw(host.toByteArray(Charsets.US_ASCII)) }
                }
            }
            .extension(0x0017) { }
            .extension(0xff01) { raw(byteArrayOf(0)) }
            .extension(0x000a) {
                vec16 {
                    u16(GROUP_X25519)
                    u16(GROUP_SECP256R1)
                    u16(GROUP_SECP384R1)
                }
            }
            .extension(0x000b) { raw(byteArrayOf(1, 0)) }
            .extension(0x0023) { }
            .extension(0x0010) {
                vec16 {
                    vec8 { raw("http/1.1".toByteArray(Charsets.US_ASCII)) }
                }
            }
            .extension(0x0005) { raw(byteArrayOf(1, 0, 0, 0, 0)) }
            .extension(0x000d) {
                vec16 { SIGNATURE_ALGORITHMS.forEach { u16(it) } }
            }
            .extension(0x0012) { }
            .extension(0x0033) {
                vec16 {
                    u16(GROUP_X25519)
                    vec16 { raw(x25519PublicKey) }
                }
            }
            .extension(0x002d) { raw(byteArrayOf(1, 1)) }
            .extension(0x002b) { vec8 { u16(0x0304); u16(0x0303) } }
            .bytes()

        val body = TlsWriter()
            .u16(0x0303)
            .raw(random)
            .vec8 { raw(sessionId) }
            .vec16 { CIPHER_SUITES.forEach { u16(it) } }
            .vec8 { u8(0) }
            .vec16 { raw(extensions) }
            .bytes()

        return TlsWriter().u8(1).u24(body.size).raw(body).bytes()
    }

    private fun TlsWriter.extension(type: Int, body: TlsWriter.() -> Unit): TlsWriter {
        u16(type)
        val payload = TlsWriter().apply(body).bytes()
        u16(payload.size)
        return raw(payload)
    }
}
