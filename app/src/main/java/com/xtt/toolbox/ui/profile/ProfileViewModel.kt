package com.xtt.toolbox.ui.profile

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xtt.toolbox.data.AppContainerProvider
import com.xtt.toolbox.data.profile.AvatarCrop
import com.xtt.toolbox.data.profile.ProfileRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ProfileUiState(
    val nickname: String = ProfileRules.DEFAULT_NICKNAME,
    val avatarPath: String? = null,
    val avatarVersion: Long = 0,
    val message: String? = null,
    val busy: Boolean = false,
    val cropBitmap: Bitmap? = null,
) {
    val avatarFile: File? get() = avatarPath?.let(::File)
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppContainerProvider.get(application).profileStore
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val profile = store.load()
        _uiState.value = ProfileUiState(
            nickname = profile.nickname,
            avatarPath = profile.avatarFile?.absolutePath,
            avatarVersion = System.currentTimeMillis(),
        )
    }

    fun updateNickname(raw: String): Boolean {
        val result = store.saveNickname(raw)
        return result.fold(
            onSuccess = { nickname ->
                _uiState.value = _uiState.value.copy(nickname = nickname, message = "昵称已保存")
                true
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    message = error.message ?: "昵称保存失败",
                )
                false
            },
        )
    }

    fun updateAvatar(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            store.loadEditableAvatar(uri).fold(
                onSuccess = { bitmap ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        cropBitmap = bitmap,
                        message = null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        message = error.message ?: "头像读取失败",
                    )
                },
            )
        }
    }

    fun cancelAvatarCrop() {
        _uiState.value = _uiState.value.copy(cropBitmap = null)
    }

    fun confirmAvatarCrop(
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        viewportPx: Float,
    ) {
        val source = _uiState.value.cropBitmap ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            val cropped = runCatching {
                AvatarCrop.crop(source, zoom, offsetX, offsetY, viewportPx)
            }.getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    message = error.message ?: "头像裁剪失败",
                )
                return@launch
            }
            store.saveAvatarBitmap(cropped).fold(
                onSuccess = { file ->
                    if (!cropped.isRecycled) cropped.recycle()
                    _uiState.value = _uiState.value.copy(
                        avatarPath = file.absolutePath,
                        avatarVersion = System.currentTimeMillis(),
                        busy = false,
                        cropBitmap = null,
                        message = "头像已更新",
                    )
                },
                onFailure = { error ->
                    if (!cropped.isRecycled) cropped.recycle()
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        message = error.message ?: "头像保存失败",
                    )
                },
            )
        }
    }

    fun clearAvatar() {
        val ok = store.clearAvatar()
        _uiState.value = _uiState.value.copy(
            avatarPath = null,
            avatarVersion = System.currentTimeMillis(),
            message = if (ok) "已恢复默认头像" else "默认头像恢复失败",
        )
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
