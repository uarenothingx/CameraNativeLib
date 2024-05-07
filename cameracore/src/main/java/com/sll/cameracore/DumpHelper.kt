package com.sll.cameracore

import java.io.File
import java.io.FileOutputStream

/**
 * @author Shenlinliang
 * @date 2024/5/7
 */
object DumpHelper {
    fun dump(data: ByteArray, w: Int, h: Int, time: Boolean = true) {
        try {
            val fp = File("sdcard/Download", "dump")
            if (!fp.exists()) fp.mkdirs()
            val f = if (time) {
                File(fp, "dump-${System.currentTimeMillis()}-${w}x${h}.NV21")
            } else {
                File(fp, "dump-${w}x${h}.NV21")
            }
            if (f.exists()) f.delete()
            f.createNewFile()
            val output = FileOutputStream(f)
            output.write(data)
            output.flush()
            output.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}
