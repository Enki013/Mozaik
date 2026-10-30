package com.personalrecords.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-facing message kept as a string resource (plus format args) until the UI shows it, so
 * it follows the in-app language. [raw] carries text that already comes translated, such as a
 * server message. [isError] replaces checking the text for words like "Error".
 */
@Immutable
data class UiMessage(
    @StringRes val resId: Int? = null,
    val args: List<Any> = emptyList(),
    val raw: String? = null,
    val isError: Boolean = false
) {
    fun resolve(context: Context): String =
        raw ?: resId?.let { context.getString(it, *args.toTypedArray()) }.orEmpty()

    companion object {
        fun of(@StringRes resId: Int, vararg args: Any) = UiMessage(resId = resId, args = args.toList())
        fun error(@StringRes resId: Int, vararg args: Any) =
            UiMessage(resId = resId, args = args.toList(), isError = true)

        /** Server text when it has any, otherwise the given resource. */
        fun serverOr(serverText: String?, @StringRes fallback: Int, vararg args: Any) =
            serverText?.takeIf { it.isNotBlank() }?.let { UiMessage(raw = it) } ?: of(fallback, *args)
    }
}

@Composable
fun UiMessage.text(): String = resolve(LocalContext.current)
