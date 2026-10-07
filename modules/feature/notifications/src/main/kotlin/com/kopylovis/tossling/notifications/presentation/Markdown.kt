package com.kopylovis.tossling.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kopylovis.tossling.core.presentation.theme.JetBrainsMono
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.core.presentation.theme.TosslingPalette

private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Bullet(val text: String) : Block
    data class Code(val text: String) : Block
}

@Composable
internal fun MarkdownBody(markdown: String, isMarkdown: Boolean, modifier: Modifier = Modifier) {
    val palette = Tossling.palette
    val blocks = remember(markdown, isMarkdown) { if (isMarkdown || looksLikeMarkdown(markdown)) parseBlocks(markdown) else listOf(Block.Paragraph(markdown.trim())) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12f.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Paragraph -> Text(text = inline(block.text, palette), style = Tossling.type.body.copy(fontSize = 16.sp, lineHeight = 23.sp), color = palette.ink)
                is Block.Heading -> Text(text = inline(block.text, palette), style = Tossling.type.headline, color = palette.ink)
                is Block.Bullet -> Text(text = inline("•  " + block.text, palette), style = Tossling.type.body.copy(fontSize = 16.sp, lineHeight = 23.sp), color = palette.ink)
                is Block.Code -> Text(
                    text = block.text,
                    style = Tossling.type.monoSmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                    color = palette.ink,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14f.dp))
                        .background(palette.codeInline)
                        .border(width = 1f.dp, color = palette.hairline, shape = RoundedCornerShape(14f.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14f.dp, vertical = 12f.dp),
                )
            }
        }
    }
}

internal fun plainText(markdown: String): String =
    markdown
        .replace(Regex("```[a-zA-Z]*\\n?"), "")
        .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1")
        .replace(Regex("(\\*\\*|__|\\*|_|`)"), "")
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun looksLikeMarkdown(text: String): Boolean = text.contains("**") || text.contains("```") || text.contains("](") || Regex("`[^`]+`").containsMatchIn(text)

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    fun flush() {
        if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    markdown.replace("\r\n", "\n").lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                if (inCode) {
                    blocks += Block.Code(code.toString().trimEnd('\n'))
                    code.clear()
                } else {
                    flush()
                }
                inCode = !inCode
            }

            inCode -> code.append(line).append('\n')
            trimmed.isEmpty() -> flush()
            trimmed.startsWith("#") -> {
                flush()
                blocks += Block.Heading(trimmed.trimStart('#').trim())
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flush()
                blocks += Block.Bullet(trimmed.drop(2))
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    if (inCode && code.isNotEmpty()) blocks += Block.Code(code.toString().trimEnd('\n'))
    flush()
    return blocks
}

private val InlineToken = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\[([^]]+)]\\(([^)\\s]+)\\)|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)|(?<!\\w)_(?!\\s)(.+?)(?<!\\s)_(?!\\w)")

private fun inline(text: String, palette: TosslingPalette): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    InlineToken.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() || groups[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(groups[1].ifEmpty { groups[2] }) }
            groups[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, background = palette.codeInline)) { append(" ${groups[3]} ") }
            groups[4].isNotEmpty() -> withLink(
                LinkAnnotation.Url(
                    url = groups[5],
                    styles = TextLinkStyles(style = SpanStyle(color = palette.accentInk, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(groups[4]) }

            groups[6].isNotEmpty() || groups[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(groups[6].ifEmpty { groups[7] }) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
