package org.lsposed.lspatch

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

/**
 * Hand-written equivalent of SwiftBackupPrem's
 * `app/src/main/aidl/org/lsposed/lspatch/IXposedServicePull.aidl`
 * (AGP compiles that AIDL; the manual on-device build has no aidl tool,
 * so the one-method stub is written out directly).
 */
interface IXposedServicePull : IInterface {
    fun requestPush(): Boolean

    abstract class Stub : Binder(), IXposedServicePull {
        companion object {
            const val DESCRIPTOR = "org.lsposed.lspatch.IXposedServicePull"
            const val TRANSACTION_requestPush = IBinder.FIRST_CALL_TRANSACTION

            fun asInterface(obj: IBinder?): IXposedServicePull? {
                if (obj == null) return null
                val iin = obj.queryLocalInterface(DESCRIPTOR)
                if (iin is IXposedServicePull) return iin
                return Proxy(obj)
            }
        }

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_requestPush -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = requestPush()
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }

        private class Proxy(private val remote: IBinder) : IXposedServicePull {
            override fun asBinder(): IBinder = remote
            override fun requestPush(): Boolean {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    remote.transact(TRANSACTION_requestPush, data, reply, 0)
                    reply.readException()
                    return reply.readInt() != 0
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }
    }
}
