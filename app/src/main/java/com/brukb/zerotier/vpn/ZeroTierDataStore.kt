package com.brukb.zerotier.vpn

import android.content.Context
import android.util.Log
import com.zerotier.sdk.DataStoreGetListener
import com.zerotier.sdk.DataStorePutListener
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

class ZeroTierDataStore(context: Context) : DataStoreGetListener, DataStorePutListener {
    private val filesDir = context.filesDir

    override fun onDataStorePut(name: String, buffer: ByteArray, secure: Boolean): Int {
        Log.d(TAG, "Writing file: $name")
        return try {
            if (name.contains('/')) {
                val parent = File(filesDir, name.substring(0, name.lastIndexOf('/')))
                parent.mkdirs()
                FileOutputStream(File(parent, name.substring(name.lastIndexOf('/') + 1))).use {
                    it.write(buffer)
                }
            } else {
                FileOutputStream(File(filesDir, name)).use { it.write(buffer) }
            }
            0
        } catch (e: FileNotFoundException) {
            -1
        } catch (e: IOException) {
            logError(e)
            -2
        } catch (e: IllegalArgumentException) {
            logError(e)
            -3
        }
    }

    override fun onDelete(name: String): Int {
        Log.d(TAG, "Deleting file: $name")
        val deleted = if (name.contains('/')) {
            File(filesDir, name).delete()
        } else {
            File(filesDir, name).delete()
        }
        return if (deleted) 0 else 1
    }

    override fun onDataStoreGet(name: String, outBuffer: ByteArray): Long {
        Log.d(TAG, "Reading file: $name")
        return try {
            val input = if (name.contains('/')) {
                val parent = File(filesDir, name.substring(0, name.lastIndexOf('/')))
                FileInputStream(File(parent, name.substring(name.lastIndexOf('/') + 1)))
            } else {
                FileInputStream(File(filesDir, name))
            }
            input.use { it.read(outBuffer).toLong() }
        } catch (_: FileNotFoundException) {
            -1
        } catch (e: IOException) {
            Log.e(TAG, "Read failed", e)
            -2
        } catch (e: Exception) {
            Log.e(TAG, "Read failed", e)
            -3
        }
    }

    private fun logError(e: Exception) {
        val writer = StringWriter()
        e.printStackTrace(PrintWriter(writer))
        Log.e(TAG, writer.toString())
    }

    companion object {
        private const val TAG = "ZeroTierDataStore"
    }
}
