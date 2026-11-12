package com.vps.qing.feature.auth

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CaptchaChallenge

/**
 * 提交时弹出的图形验证码对话框（替代原先内嵌在页面里的 CaptchaField）。
 *
 * 交互：点「注册 / 获取验证码 / 提交找回」→ 弹窗 → 输入 → 校验通过 →
 * 由 AuthViewModel 自动执行刚才那个动作，不需要用户再点一次。
 * 图片来自 /console/v1/captcha 的 data.html 内嵌 base64，直接解成 Bitmap 渲染，不套 WebView。
 */
@Composable
fun CaptchaDialog(
    challenge: CaptchaChallenge?,
    loading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember(challenge?.token) { mutableStateOf("") }
    val bitmap = remember(challenge?.imageBase64) {
        challenge?.imageBase64?.let { b64 ->
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(Strings.CAPTCHA_DIALOG_TITLE, style = MaterialTheme.typography.titleLarge)
                Text(
                    Strings.CAPTCHA_DIALOG_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = Strings.CAPTCHA_IMAGE,
                            modifier = Modifier
                                .width(130.dp)
                                .height(48.dp)
                                .clickable(enabled = !loading) { onRefresh() }
                        )
                    } else {
                        OutlinedButton(onClick = onRefresh, enabled = !loading) {
                            Text(Strings.CAPTCHA_LOAD)
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(Strings.CAPTCHA_INPUT) },
                        singleLine = true,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
                    OutlinedButton(onClick = onRefresh, enabled = !loading) {
                        Text(Strings.CAPTCHA_CHANGE)
                    }
                    Button(
                        onClick = { onSubmit(input) },
                        enabled = !loading && challenge != null && input.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(Strings.CAPTCHA_VERIFY)
                        }
                    }
                }
            }
        }
    }
}
