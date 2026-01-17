package io.nekohasekai.sfa.xposed.hooks.hidevpn

import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import de.robv.android.xposed.XposedHelpers
import io.nekohasekai.sfa.xposed.HookErrorStore
import io.nekohasekai.sfa.xposed.PrivilegeSettingsStore
import io.nekohasekai.sfa.xposed.hooks.SafeMethodHook
import io.nekohasekai.sfa.xposed.hooks.XHook
import java.io.FileDescriptor
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class HookNetworkInterfaceGetName(private val classLoader: ClassLoader) : XHook {
    private companion object {
        private const val SOURCE = "HookNetworkInterfaceGetName"
        private const val MAX_NAME_LEN = 15
        private const val MAX_SUFFIX = 63
        private const val NLMSG_HEADER_LEN = 16
        private const val IFINFO_MSG_LEN = 16
        private const val NLA_HEADER_LEN = 4
        private const val RTM_NEWLINK = 16
        private const val IFLA_IFNAME = 3
        private const val NLM_F_REQUEST = 0x1
        private const val NLM_F_ACK = 0x4
        private const val NLMSG_ERROR = 2
        private const val IFF_UP = 0x1
    }

    private val netlinkSocketAddressClass by lazy { Class.forName("android.system.NetlinkSocketAddress") }
    private val netlinkSocketAddressCtor by lazy {
        netlinkSocketAddressClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    }

    private val seq = AtomicInteger(1)

    override fun injectHook() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hookJniGetNameApi33Plus()
        } else {
            hookJniGetNameLegacy()
        }
    }

    private fun hookJniGetNameApi33Plus() {
        val vpnClass = findVpnClass()
        val depsClass = XposedHelpers.findClass("${vpnClass.name}\$Dependencies", classLoader)
        XposedHelpers.findAndHookMethod(
            depsClass,
            "jniGetName",
            vpnClass,
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    processJniGetNameResult(param)
                }
            },
        )
        HookErrorStore.i(SOURCE, "Hooked ${depsClass.name}.jniGetName (API 33+)")
    }

    private fun hookJniGetNameLegacy() {
        val cls = findVpnClass()
        XposedHelpers.findAndHookMethod(
            cls,
            "jniGetName",
            Int::class.javaPrimitiveType,
            object : SafeMethodHook(SOURCE) {
                override fun afterHook(param: MethodHookParam) {
                    processJniGetNameResult(param)
                }
            },
        )
        HookErrorStore.i(SOURCE, "Hooked ${cls.name}.jniGetName (legacy)")
    }

    private fun processJniGetNameResult(param: de.robv.android.xposed.XC_MethodHook.MethodHookParam) {
        val result = param.result
        if (result !is String) {
            if (result != null) {
                HookErrorStore.e(SOURCE, "jniGetName returned unexpected type: ${result.javaClass.name}")
            }
            return
        }
        if (!PrivilegeSettingsStore.shouldRenameInterface()) return
        if (!isTunInterface(result)) return
        val prefix = PrivilegeSettingsStore.interfacePrefix()
        val renamed = renameInterface(result, prefix) ?: return
        param.result = renamed
    }

    private fun findVpnClass(): Class<*> = XposedHelpers.findClass("com.android.server.connectivity.Vpn", classLoader)

    private fun isTunInterface(name: String): Boolean = name.startsWith("tun")

    private fun renameInterface(oldName: String, prefix: String): String? {
        val oldIndex = getInterfaceIndex(oldName)
        if (oldIndex <= 0) {
            HookErrorStore.e(SOURCE, "rename interface: old name not found (old=$oldName)")
            return null
        }
        val newName = findAvailableName(prefix)
        if (newName == null) {
            HookErrorStore.e(SOURCE, "rename interface: no available name (prefix=$prefix)")
            return null
        }
        if (newName == oldName) {
            return oldName
        }
        if (!renameWithNetlink(oldIndex, newName)) {
            HookErrorStore.e(SOURCE, "rename failed: $oldName -> $newName")
            return null
        }
        val newIndex = getInterfaceIndex(newName)
        if (newIndex <= 0) {
            HookErrorStore.e(
                SOURCE,
                "rename interface: new name not found (old=$oldName index=$oldIndex)",
            )
            return null
        }
        HookErrorStore.i(SOURCE, "rename interface: $oldName -> $newName")
        return newName
    }

    private fun getInterfaceIndex(name: String): Int = Os.if_nametoindex(name)

    private fun findAvailableName(prefix: String): String? {
        val base = prefix.trim()
        if (base.isEmpty()) {
            return null
        }
        for (i in 0..MAX_SUFFIX) {
            val candidate = buildInterfaceName(base, i) ?: return null
            if (getInterfaceIndex(candidate) == 0) {
                return candidate
            }
        }
        return null
    }

    private fun buildInterfaceName(prefix: String, suffix: Int): String? {
        val suffixText = suffix.toString()
        val maxPrefixLen = MAX_NAME_LEN - suffixText.length
        if (maxPrefixLen <= 0) {
            return null
        }
        val trimmed = if (prefix.length > maxPrefixLen) {
            prefix.substring(0, maxPrefixLen)
        } else {
            prefix
        }
        return trimmed + suffixText
    }

    private fun renameWithNetlink(index: Int, newName: String): Boolean {
        val fd = openNetlinkSocket()
        try {
            val renameResult = sendNetlinkMessage(
                fd,
                buildLinkMessage(index, newName, 0, 0, seq.getAndIncrement()),
                OsConstants.EBUSY,
            ) ?: return false
            if (renameResult == 0) {
                return true
            }
            if (renameResult != OsConstants.EBUSY) {
                HookErrorStore.e(SOURCE, "rename interface: netlink ack errno=$renameResult")
                return false
            }
            val downResult = sendNetlinkMessage(
                fd,
                buildLinkMessage(index, null, 0, IFF_UP, seq.getAndIncrement()),
            ) ?: return false
            if (downResult != 0) {
                HookErrorStore.e(SOURCE, "rename interface: set down failed errno=$downResult")
                return false
            }
            val retryResult = sendNetlinkMessage(
                fd,
                buildLinkMessage(index, newName, 0, 0, seq.getAndIncrement()),
            ) ?: return false
            if (retryResult != 0) {
                HookErrorStore.e(SOURCE, "rename interface: retry failed errno=$retryResult")
                return false
            }
            val upResult = sendNetlinkMessage(
                fd,
                buildLinkMessage(index, null, IFF_UP, IFF_UP, seq.getAndIncrement()),
            )
            if (upResult != null && upResult != 0) {
                HookErrorStore.w(SOURCE, "rename interface: set up failed errno=$upResult")
            }
            return true
        } catch (e: Throwable) {
            HookErrorStore.e(SOURCE, "rename interface: netlink exception", e)
            return false
        } finally {
            try {
                Os.close(fd)
            } catch (e: Throwable) {
                HookErrorStore.w(SOURCE, "close netlink socket failed", e)
            }
        }
    }

    private fun openNetlinkSocket(): FileDescriptor {
        val fd = Os.socket(OsConstants.AF_NETLINK, OsConstants.SOCK_RAW, OsConstants.NETLINK_ROUTE)
        Os.setsockoptTimeval(
            fd,
            OsConstants.SOL_SOCKET,
            OsConstants.SO_RCVTIMEO,
            StructTimeval.fromMillis(200),
        )
        val address = buildNetlinkAddress()
        Os.connect(fd, address)
        return fd
    }

    private fun buildNetlinkAddress(): SocketAddress = netlinkSocketAddressCtor.newInstance(0, 0) as SocketAddress

    private fun buildLinkMessage(index: Int, ifName: String?, flags: Int, change: Int, seq: Int): ByteArray {
        val nameBytes = ifName?.let { (it + "\u0000").toByteArray(Charsets.US_ASCII) }
        val attrLen = if (nameBytes != null) NLA_HEADER_LEN + nameBytes.size else 0
        val attrAligned = align(attrLen)
        val totalLength = NLMSG_HEADER_LEN + IFINFO_MSG_LEN + attrAligned
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.nativeOrder())
        buffer.putInt(totalLength)
        buffer.putShort(RTM_NEWLINK.toShort())
        buffer.putShort((NLM_F_REQUEST or NLM_F_ACK).toShort())
        buffer.putInt(seq)
        buffer.putInt(Os.getpid())
        buffer.put(OsConstants.AF_UNSPEC.toByte())
        buffer.put(0.toByte())
        buffer.putShort(0)
        buffer.putInt(index)
        buffer.putInt(flags)
        buffer.putInt(change)
        if (nameBytes != null) {
            buffer.putShort(attrLen.toShort())
            buffer.putShort(IFLA_IFNAME.toShort())
            buffer.put(nameBytes)
            val pad = attrAligned - attrLen
            repeat(pad) {
                buffer.put(0.toByte())
            }
        }
        return buffer.array()
    }

    private fun align(length: Int): Int = (length + 3) and -4

    private fun sendNetlinkMessage(fd: FileDescriptor, message: ByteArray, suppressErrno: Int? = null): Int? {
        Os.write(fd, message, 0, message.size)
        val ack = readNetlinkAck(fd)
        if (ack == null) {
            HookErrorStore.e(SOURCE, "rename interface: netlink ack missing")
            return null
        }
        if (ack.errno != 0 && ack.errno != suppressErrno) {
            HookErrorStore.e(
                SOURCE,
                "rename interface: netlink ack errno=${ack.errno} seq=${ack.seq} pid=${ack.pid}",
            )
        }
        return ack.errno
    }

    private data class NetlinkAck(val errno: Int, val seq: Int, val pid: Int)

    private fun readNetlinkAck(fd: FileDescriptor): NetlinkAck? {
        val buffer = ByteArray(4096)
        val length = Os.read(fd, buffer, 0, buffer.size)
        if (length <= 0 || length < NLMSG_HEADER_LEN) {
            return null
        }
        val byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.nativeOrder())
        val msgLen = byteBuffer.int
        val msgType = byteBuffer.short.toInt() and 0xFFFF
        byteBuffer.short
        val msgSeq = byteBuffer.int
        val msgPid = byteBuffer.int
        if (msgLen < NLMSG_HEADER_LEN || msgLen > length) {
            return null
        }
        if (msgType != NLMSG_ERROR) {
            return NetlinkAck(0, msgSeq, msgPid)
        }
        if (byteBuffer.remaining() < 4) {
            return null
        }
        val error = byteBuffer.int
        val errno = if (error == 0) 0 else -error
        return NetlinkAck(errno, msgSeq, msgPid)
    }
}
