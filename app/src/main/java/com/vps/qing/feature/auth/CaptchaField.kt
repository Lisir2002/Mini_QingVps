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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.vps.qing.data.repository.CaptchaChallenge
import com.vps.qing.data.repository.CaptchaTicket

/**
 * 原生图形验证码组件（A 路线：不套 WebView）。
 *
 * 站点 `GET /console/v1/captcha` 返回 data.html，其中图片是 `data:image/png;base64,...`、
 * token 在 `var tokenTag = '...'`。这里直接把 base64 解成 Bitmap 渲染，用户输入后
 * 由 ViewModel 调 `/captcha/tp_captcha/index/verify` 校验，得到 (captcha, token) 凭证。
 * 点图片或「换一张」会重新拉取，避免依赖站点自带的 jQuery/CDN 脚本。
 */
@Composable
fun CaptchaField(
    challenge: CaptchaChallenge?,
    ticket: CaptchaTicket?,
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图形验证码，点击可更换",
                    modifier = Modifier
                        .width(130.dp)
                        .height(48.dp)
                        .clickable(enabled = !loading) { onRefresh() }
                )
            } else {
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text("加载验证码")
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("图形验证码") },
                singleLine = true,
                enabled = challenge != null && ticket == null && !loading,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                )
            )
        }

        if (ticket != null) {
            Text(
                "图形验证通过，可以获取邮箱验证码",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSubmit(input) },
                    enabled = !loading && challenge != null && input.isNotBlank()
                ) {
                    Text(if (loading) "验证中…" else "验证")
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text("换一张")
                }
            }
        }
    }
}
