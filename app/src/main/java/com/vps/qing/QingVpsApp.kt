package com.vps.qing

import android.app.Application
import com.vps.qing.core.AppContainer
import com.vps.qing.core.notify.QingNotifier

class QingVpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        // v1.4.17：按业务分类创建通知渠道（minSdk 26 起渠道必填）。
        // 只建渠道不代表会发通知，发送仍受权限开关 / 总开关 / 免打扰四道闸控制。
        QingNotifier.createChannels(this)
    }
}
