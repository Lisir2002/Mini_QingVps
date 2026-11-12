package com.vps.qing.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 骨架阶段用手动 DI（AppContainer），避免引入 Hilt/KSP 带来的构建配置复杂度。
 * 后续接入 Hilt 时，只需把各 Screen 里的 simpleViewModel 换成 hiltViewModel()。
 */
inline fun <reified VM : ViewModel> simpleViewModelFactory(
    crossinline create: () -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
