package io.github.xhr666.wristchat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 安装结果回调(仅用于写日志/提示) */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -1)
        val msg = when (code) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> "等待确认安装"
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> "安装成功"
            else -> "安装未完成(状态 $code)"
        }
        io.github.xhr666.wristchat.data.AppLog.i("install", msg)
    }
}
