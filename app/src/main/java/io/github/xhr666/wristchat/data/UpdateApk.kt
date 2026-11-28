package io.github.xhr666.wristchat.data

/** 下载目录里比当前已安装版本更新的包要留着。 */
object UpdateApk {
    fun newerThanInstalled(apkVersionCode: Long, installedVersionCode: Long): Boolean =
        apkVersionCode > installedVersionCode
}
