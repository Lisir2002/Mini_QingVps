package com.vps.qing.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vps.qing.core.network.ApiResult
import com.vps.qing.core.network.HtmlFallbackException
import com.vps.qing.data.repository.AccountInfo
import com.vps.qing.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class ProfileUiState(
    val loading: Boolean = true,
    val account: AccountInfo? = null,
    val error: String? = null,
    val loggedOut: Boolean = false
)

class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            when (val result = repo.account()) {
                is ApiResult.Success -> _ui.update {
                    it.copy(loading = false, account = result.data, error = null)
                }
                is ApiResult.Failure -> _ui.update {
                    it.copy(loading = false, error = "[${result.code}] ${result.msg}")
                }
                is ApiResult.Error -> _ui.update {
                    it.copy(loading = false, error = friendly(result.throwable))
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _ui.update { it.copy(loggedOut = true) }
        }
    }

    private fun friendly(t: Throwable): String = when (t) {
        is HtmlFallbackException -> "接口返回了网页而不是 JSON，请确认登录态或站点版本"
        is IOException -> "网络连接失败，请检查网络"
        else -> t.message ?: "未知错误"
    }
}
