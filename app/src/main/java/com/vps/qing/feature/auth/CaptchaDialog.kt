package com.vps.qing.feature.auth

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vps.qing.core.ui.AppIcons
import com.vps.qing.core.ui.CartoonButton
import com.vps.qing.core.ui.theme.CartoonColors
import com.vps.qing.core.ui.CartoonKit
import com.vps.qing.core.ui.GlassSurface
import com.vps.qing.core.ui.Strings
import com.vps.qing.data.repository.CaptchaChallenge

/** 提交时弹出的图形验证码对话框（磨砂玻璃）。验证通过后自动继续刚才的动作。 */
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
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = CartoonKit.SheetShape,
            alpha = 0.94f,
            elevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CartoonColors.BrandSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Captcha, null, tint = CartoonColors.Brand, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Strings.CAPTCHA_DIALOG_TITLE,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = CartoonColors.Ink
                    )
                }
                Text(
                    Strings.CAPTCHA_DIALOG_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = CartoonColors.InkSub
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
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !loading) { onRefresh() }
                        )
                    } else {
                        Box(
                            Modifier
                                .width(130.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CartoonColors.BrandSoft)
                                .clickable(enabled = !loading) { onRefresh() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(Strings.CAPTCHA_LOAD, style = MaterialTheme.typography.labelSmall, color = CartoonColors.Brand)
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(Strings.CAPTCHA_INPUT) },
                        singleLine = true,
                        enabled = !loading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        )
                    )
                }

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(Strings.CANCEL, color = CartoonColors.InkSub) }
                    OutlinedButton(onClick = onRefresh, enabled = !loading) {
                        Text(Strings.CAPTCHA_CHANGE, color = CartoonColors.Brand)
                    }
                    Spacer(Modifier.weight(1f))
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = CartoonColors.Brand)
                    } else {
                        CartoonButton(
                            text = Strings.CAPTCHA_VERIFY,
                            onClick = { onSubmit(input) },
                            enabled = challenge != null && input.isNotBlank(),
                            icon = AppIcons.Check
                        )
                    }
                }
            }
        }
    }
}
