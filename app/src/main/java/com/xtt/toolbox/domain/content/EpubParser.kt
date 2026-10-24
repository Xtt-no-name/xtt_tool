package com.xtt.toolbox.domain.content

import com.xtt.toolbox.data.model.ChapterDetectionSource
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class EpubParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 无第三方依赖的 EPUB2/EPUB3 解析器。
 *
 * 安全边界：只读取 ZIP 条目，不按归档路径写文件；限制条目数量、总解压大小和单条目大小；
 * 禁用 XML 外部实体；拒绝绝对路径、`..`、反斜杠和外部 URL。图片只提取到内存中的有界字节，
 * 由导入服务写入书籍专属目录。
 */
class EpubParser {
    private var readBytes = 0L

    fun parse(file: File, sourceName: String): ParsedEpub {
        readBytes = 0L
        if (!file.isFile) throw EpubParseException("EPUB 文件不存在或不可读取")
        if (file.length() > MAX_ARCHIVE_BYTES) {
            throw EpubParseException("EPUB 文件过大，最大支持 ${MAX_ARCHIVE_BYTES / 1024 / 1024} MiB")
        }
        ZipFile(file).use { zip ->
            validateEntries(zip)
            val containerPath = "META-INF/container.xml"
            val container = readXml(zip, containerPath, "META-INF/container.xml")
            val rootfile = container.documentElement
                .descendants()
                .firstOrNull { it.localNameOrTag() == "rootfile" }
                ?.attr("full-path")
                ?.takeIf { it.isNotBlank() }
                ?: throw EpubParseException("EPUB 缺少 container.xml 的 rootfile")
            val opfPath = normalizeArchivePath("", rootfile)
                ?: throw EpubParseException("EPUB package 路径非法")
            if (zip.getEntry(opfPath) == null) {
                throw EpubParseException("EPUB package 文件不存在")
            }
            val opf = readXml(zip, opfPath, "OPF")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val packageElement = opf.documentElement
            val manifest = readManifest(packageElement, opfDir)
            if (manifest.isEmpty()) throw EpubParseException("EPUB manifest 为空")
            val spine = readSpine(packageElement, manifest)
            if (spine.isEmpty()) throw EpubParseException("EPUB spine 没有可阅读章节")
            val metadata = readMetadata(packageElement)
            val title = metadata.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: sourceName.removeSuffixIgnoreCase(".epub").trim().ifBlank { "未命名 EPUB" }
            val author = metadata.author?.trim()?.takeIf { it.isNotEmpty() }
            val language = metadata.language?.trim()?.takeIf { it.isNotEmpty() }
            val navEntries = readNavigation(zip, manifest, opfDir)
            val warnings = mutableListOf<String>()
            val resourcesByPath = mutableMapOf<String, ExtractedResource>()
            val resourcesById = mutableMapOf<String, ExtractedResource>()

            fun resourceForPath(path: String, alt: String?, cover: Boolean = false): String? {
                resourcesByPath[path]?.let { return it.resource.resourceId }
                val item = manifest.values.firstOrNull { it.path == path }
                if (item != null && !item.mediaType.startsWith("image/", ignoreCase = true)) {
                    warnings += "忽略非图片资源：$path"
                    return null
                }
                if (zip.getEntry(path) == null) {
                    warnings += "图片缺失：$path"
                    return null
                }
                val bytes = try {
                    readEntry(zip, path, MAX_RESOURCE_BYTES)
                } catch (error: Throwable) {
                    warnings += "图片读取失败：$path"
                    return null
                }
                val detected = detectImageType(bytes)
                if (detected == null) {
                    warnings += "不支持的图片格式：$path"
                    return null
                }
                val resourceId = "epub-res-${UUID.randomUUID()}"
                val relativePath = "epub/resources/$resourceId.${detected.extension}"
                val resource = BookResource(
                    resourceId = resourceId,
                    relativePath = relativePath,
                    mediaType = detected.mediaType,
                    sizeBytes = bytes.size.toLong(),
                    sha256 = sha256(bytes),
                    kind = if (cover) BookResourceKind.COVER else BookResourceKind.IMAGE,
                    alt = alt?.takeIf { it.isNotBlank() },
                )
                val extracted = ExtractedResource(resource, bytes)
                resourcesByPath[path] = extracted
                resourcesById[resourceId] = extracted
                return resourceId
            }

            fun resourceFor(href: String, alt: String?, cover: Boolean = false): String? {
                val path = normalizeArchivePath(opfDir, href) ?: run {
                    warnings += "忽略非法图片路径：$href"
                    return null
                }
                return resourceForPath(path, alt, cover)
            }

            // 封面只保存为资源，不强制插入正文；封面缺失允许继续。
            val coverHref = metadata.coverId?.let { manifest[it]?.path }
                ?: manifest.values.firstOrNull {
                    it.properties.contains("cover-image", ignoreCase = true)
                }?.path
            if (coverHref != null) {
                resourceForPath(coverHref, metadata.title, cover = true)
            }

            val textBuilder = StringBuilder()
            val blocks = mutableListOf<BookBlock>()
            val drafts = mutableListOf<EpubChapterDraft>()
            var blockOrder = 0

            fun appendTextBlock(
                text: String,
                type: BookBlockType,
                level: Int,
                style: BookBlockStyle?,
                spans: List<InlineSpan>,
            ) {
                val normalized = normalizeText(text)
                if (normalized.isBlank()) return
                val start = textBuilder.length
                textBuilder.append(normalized)
                if (!normalized.endsWith('\n')) textBuilder.append('\n')
                val end = textBuilder.length
                if (start == end) return
                blocks += BookBlock(
                    blockId = "epub-block-${blockOrder}",
                    order = blockOrder++,
                    type = type,
                    startOffset = start,
                    endOffset = end,
                    level = level,
                    style = style,
                    spans = spans,
                )
            }

            fun appendImage(resourceId: String?, alt: String?, style: BookBlockStyle?) {
                val offset = textBuilder.length
                blocks += BookBlock(
                    blockId = "epub-block-${blockOrder}",
                    order = blockOrder++,
                    type = BookBlockType.IMAGE,
                    startOffset = offset,
                    endOffset = offset,
                    resourceId = resourceId,
                    alt = alt,
                    style = style,
                )
            }

            spine.forEachIndexed { index, spineItem ->
                val chapterStart = textBuilder.length
                val navTitle = navEntries.firstOrNull { it.path == spineItem.path }?.title
                var firstHeading: String? = null
                val xhtml = try {
                    readXml(zip, spineItem.path, spineItem.path)
                } catch (error: Throwable) {
                    warnings += "章节读取失败：${spineItem.path}"
                    null
                }
                if (xhtml != null) {
                    val cssRules = EpubCss.parseRules(xhtml)
                    val body = xhtml.documentElement.descendants()
                        .firstOrNull { it.localNameOrTag() == "body" }
                        ?: xhtml.documentElement
                    val elements = body.blockChildren()
                    elements.forEach { element ->
                        val blockStyle = EpubCss.blockStyle(element, cssRules)
                        when (val tag = element.localNameOrTag().lowercase()) {
                            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                                val level = tag.removePrefix("h").toIntOrNull() ?: 1
                                val pieces = collectInlinePieces(
                                    element,
                                    opfDir,
                                    ::resourceFor,
                                    warnings,
                                    cssRules = cssRules,
                                )
                                if (firstHeading == null) {
                                    firstHeading = pieces.filterIsInstance<InlinePiece.Text>()
                                        .joinToString(" ") { it.text }
                                        .trim()
                                        .takeIf { it.isNotEmpty() }
                                }
                                emitPieces(
                                    pieces,
                                    BookBlockType.HEADING,
                                    level,
                                    blockStyle,
                                    ::appendTextBlock,
                                    ::appendImage,
                                )
                            }

                            "p", "li", "blockquote", "pre", "div", "section", "article" -> {
                                val pieces = collectInlinePieces(
                                    element,
                                    opfDir,
                                    ::resourceFor,
                                    warnings,
                                    cssRules = cssRules,
                                )
                                if (pieces.isEmpty()) {
                                    element.blockChildren().forEach { child ->
                                        val childPieces = collectInlinePieces(
                                            child,
                                            opfDir,
                                            ::resourceFor,
                                            warnings,
                                            cssRules = cssRules,
                                        )
                                        emitPieces(
                                            childPieces,
                                            BookBlockType.PARAGRAPH,
                                            0,
                                            EpubCss.blockStyle(child, cssRules),
                                            ::appendTextBlock,
                                            ::appendImage,
                                        )
                                    }
                                } else {
                                    emitPieces(
                                        pieces,
                                        BookBlockType.PARAGRAPH,
                                        0,
                                        blockStyle,
                                        ::appendTextBlock,
                                        ::appendImage,
                                    )
                                }
                            }

                            "ul", "ol" -> {
                                element.childElements()
                                    .filter { it.localNameOrTag().equals("li", ignoreCase = true) }
                                    .forEach { listItem ->
                                        emitPieces(
                                            collectInlinePieces(
                                                listItem,
                                                opfDir,
                                                ::resourceFor,
                                                warnings,
                                                cssRules = cssRules,
                                            ),
                                            BookBlockType.PARAGRAPH,
                                            0,
                                            EpubCss.blockStyle(listItem, cssRules),
                                            ::appendTextBlock,
                                            ::appendImage,
                                        )
                                    }
                            }

                            "img", "image" -> {
                                val alt = element.attr("alt")
                                val src = element.attr("src")
                                    .ifBlank { element.attr("href") }
                                    .ifBlank { element.attr("xlink:href") }
                                if (src.isNotBlank()) {
                                    appendImage(resourceFor(src, alt), alt, blockStyle)
                                }
                            }

                            "br" -> appendTextBlock("\n", BookBlockType.PARAGRAPH, 0, null, emptyList())
                            "script", "style", "nav" -> Unit
                            else -> {
                                val pieces = collectInlinePieces(
                                    element,
                                    opfDir,
                                    ::resourceFor,
                                    warnings,
                                    cssRules = cssRules,
                                )
                                emitPieces(
                                    pieces,
                                    BookBlockType.PARAGRAPH,
                                    0,
                                    blockStyle,
                                    ::appendTextBlock,
                                    ::appendImage,
                                )
                            }
                        }
                    }
                }
                val chapterEnd = textBuilder.length
                val nav = navEntries.firstOrNull { it.path == spineItem.path }
                val fallbackTitle = firstHeading?.take(120)
                    ?: spineItem.path.substringAfterLast('/').substringBeforeLast('.').ifBlank {
                        "第 ${index + 1} 章"
                    }
                drafts += EpubChapterDraft(
                    title = navTitle ?: fallbackTitle,
                    order = index,
                    startOffset = chapterStart,
                    endOffset = chapterEnd,
                    headingOffset = chapterStart,
                    level = nav?.level ?: 0,
                    detectionSource = when {
                        nav != null -> ChapterDetectionSource.EPUB_NAV
                        firstHeading != null -> ChapterDetectionSource.EPUB_HEADING
                        else -> ChapterDetectionSource.EPUB_FALLBACK
                    },
                    confidence = if (nav != null) 1f else 0.7f,
                )
            }

            val text = textBuilder.toString()
            if (text.isBlank() && blocks.none { it.type == BookBlockType.IMAGE }) {
                throw EpubParseException("EPUB 没有可显示的正文内容")
            }
            if (text.length > MAX_TEXT_CHARS) {
                throw EpubParseException("EPUB 正文过长，无法导入")
            }
            return ParsedEpub(
                title = title,
                author = author,
                language = language,
                text = text,
                chapters = drafts,
                blocks = blocks,
                resources = resourcesById.values.toList(),
                warnings = warnings,
            )
        }
    }

    private data class ManifestItem(
        val id: String,
        val path: String,
        val mediaType: String,
        val properties: String,
    )

    private data class SpineItem(
        val path: String,
        val mediaType: String,
    )

    private data class NavEntry(
        val title: String,
        val path: String,
        val level: Int,
    )

    private data class Metadata(
        val title: String?,
        val author: String?,
        val language: String?,
        val coverId: String?,
    )

    private sealed interface InlinePiece {
        data class Text(
            val text: String,
            val flags: EpubCss.InlineFlags,
        ) : InlinePiece

        data class Image(
            val resourceId: String?,
            val alt: String?,
            val style: BookBlockStyle?,
        ) : InlinePiece
    }

    private data class StyledText(
        val text: String,
        val spans: List<InlineSpan>,
    )

    private fun emitPieces(
        pieces: List<InlinePiece>,
        textType: BookBlockType,
        level: Int,
        blockStyle: BookBlockStyle?,
        appendText: (String, BookBlockType, Int, BookBlockStyle?, List<InlineSpan>) -> Unit,
        appendImage: (String?, String?, BookBlockStyle?) -> Unit,
    ) {
        val pending = mutableListOf<InlinePiece.Text>()
        fun flush() {
            val styled = buildStyledText(pending) ?: return
            appendText(styled.text, textType, level, blockStyle, styled.spans)
        }
        pieces.forEach { piece ->
            when (piece) {
                is InlinePiece.Text -> pending += piece
                is InlinePiece.Image -> {
                    flush()
                    pending.clear()
                    appendImage(piece.resourceId, piece.alt, piece.style)
                }
            }
        }
        flush()
    }

    private fun collectInlinePieces(
        element: Element,
        opfDir: String,
        resourceFor: (String, String?, Boolean) -> String?,
        warnings: MutableList<String>,
        cssRules: List<EpubCss.Rule>,
    ): List<InlinePiece> {
        val pieces = mutableListOf<InlinePiece>()

        fun walk(node: Node, inherited: EpubCss.InlineFlags) {
            when (node.nodeType) {
                Node.TEXT_NODE -> {
                    val text = node.nodeValue ?: return
                    if (text.isNotBlank()) pieces += InlinePiece.Text(text, inherited)
                }

                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    val tag = child.localNameOrTag().lowercase()
                    val childFlags = EpubCss.inlineFlags(child, inherited, cssRules)
                    when (tag) {
                        "script", "style", "nav" -> return
                        "br" -> pieces += InlinePiece.Text("\n", inherited)
                        "img", "image" -> {
                            val src = child.attr("src")
                                .ifBlank { child.attr("href") }
                                .ifBlank { child.attr("xlink:href") }
                            if (src.isNotBlank()) {
                                val alt = child.attr("alt")
                                val id = resourceFor(src, alt, false)
                                pieces += InlinePiece.Image(
                                    resourceId = id,
                                    alt = alt,
                                    style = EpubCss.blockStyle(child, cssRules),
                                )
                            }
                        }

                        else -> {
                            val children = child.childNodes
                            for (i in 0 until children.length) walk(children.item(i), childFlags)
                        }
                    }
                }
            }
        }
        val rootFlags = EpubCss.inlineFlags(element, EpubCss.InlineFlags(), cssRules)
        val children = element.childNodes
        for (i in 0 until children.length) walk(children.item(i), rootFlags)
        return pieces
    }

    /**
     * 将带样式的文本片段规范化为块文本，并把样式区间映射到块内相对偏移。
     * 空白折叠规则与 [normalizeText] 保持一致，但保留每个字符的样式。
     */
    private fun buildStyledText(pieces: List<InlinePiece.Text>): StyledText? {
        val chars = StringBuilder()
        val flags = ArrayList<EpubCss.InlineFlags>()

        fun removeLast() {
            if (chars.isNotEmpty()) {
                chars.setLength(chars.length - 1)
                flags.removeAt(flags.lastIndex)
            }
        }

        pieces.forEach { piece ->
            piece.text.forEach { raw ->
                val c = when (raw) {
                    '\u00a0' -> ' '
                    '\t', '\r' -> ' '
                    else -> raw
                }
                when {
                    c == ' ' -> {
                        if (chars.isEmpty() || chars.last() == ' ' || chars.last() == '\n') {
                            return@forEach
                        }
                        chars.append(c)
                        flags += piece.flags
                    }

                    c == '\n' -> {
                        while (chars.isNotEmpty() && chars.last() == ' ') removeLast()
                        if (chars.isNotEmpty() && chars.last() != '\n') {
                            chars.append('\n')
                            flags += EpubCss.InlineFlags()
                        }
                    }

                    c.isWhitespace() -> Unit

                    else -> {
                        chars.append(c)
                        flags += piece.flags
                    }
                }
            }
        }

        var start = 0
        var end = chars.length
        while (start < end && chars[start].isWhitespace()) start++
        while (end > start && chars[end - 1].isWhitespace()) end--
        if (start >= end) return null
        val text = chars.substring(start, end)
        val spans = mutableListOf<InlineSpan>()
        var runStart = start
        var current: EpubCss.InlineFlags? = flags[start]
        for (i in start + 1..end) {
            val next = if (i < end) flags[i] else null
            if (next != current) {
                if (current != null && current.isStyled) {
                    spans += InlineSpan(
                        start = runStart - start,
                        end = i - start,
                        bold = current.bold,
                        italic = current.italic,
                        underline = current.underline,
                        strike = current.strike,
                        link = current.link,
                    )
                }
                runStart = i
                current = next
            }
        }
        return StyledText(text, spans)
    }

    private val EpubCss.InlineFlags.isStyled: Boolean
        get() = bold || italic || underline || strike || link

    private fun readManifest(packageElement: Element, opfDir: String): Map<String, ManifestItem> {
        val manifest = packageElement.descendants()
            .firstOrNull { it.localNameOrTag() == "manifest" } ?: return emptyMap()
        return manifest.childElements().mapNotNull { item ->
            val id = item.attr("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = item.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = normalizeArchivePath(opfDir, href) ?: return@mapNotNull null
            id to ManifestItem(
                id = id,
                path = path,
                mediaType = item.attr("media-type"),
                properties = item.attr("properties"),
            )
        }.toMap()
    }

    private fun readSpine(
        packageElement: Element,
        manifest: Map<String, ManifestItem>,
    ): List<SpineItem> {
        val spine = packageElement.descendants()
            .firstOrNull { it.localNameOrTag() == "spine" } ?: return emptyList()
        return spine.childElements().mapNotNull { itemRef ->
            val idref = itemRef.attr("idref")
            val item = manifest[idref] ?: return@mapNotNull null
            if (!isXhtml(item.mediaType, item.path)) return@mapNotNull null
            SpineItem(item.path, item.mediaType)
        }
    }

    private fun readMetadata(packageElement: Element): Metadata {
        val metadata = packageElement.descendants()
            .firstOrNull { it.localNameOrTag() == "metadata" }
        val all = metadata?.descendants()?.toList().orEmpty()
        fun firstText(name: String): String? =
            all.firstOrNull { it.localNameOrTag() == name }?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val coverId = all.firstOrNull {
            it.localNameOrTag() == "meta" && it.attr("name").equals("cover", ignoreCase = true)
        }?.attr("content")?.takeIf { it.isNotBlank() }
        return Metadata(
            title = firstText("title"),
            author = firstText("creator"),
            language = firstText("language"),
            coverId = coverId,
        )
    }

    private fun readNavigation(
        zip: ZipFile,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
    ): List<NavEntry> {
        val navItem = manifest.values.firstOrNull {
            it.properties.split(' ', '\t').any { part -> part.equals("nav", true) }
        }
        if (navItem != null) {
            val doc = runCatching { readXml(zip, navItem.path, "EPUB3 导航") }.getOrNull()
            if (doc != null) {
                val nav = doc.documentElement.descendants().firstOrNull {
                    it.localNameOrTag() == "nav" &&
                        (it.attr("type").contains("toc", ignoreCase = true) ||
                            it.attr("epub:type").contains("toc", ignoreCase = true))
                }
                if (nav != null) {
                    val result = mutableListOf<NavEntry>()
                    fun walk(element: Element, level: Int) {
                        element.childElements().forEach { child ->
                            when (child.localNameOrTag().lowercase()) {
                                "a" -> {
                                    val href = child.attr("href")
                                    val path = normalizeArchivePath(
                                        navItem.path.substringBeforeLast('/', ""),
                                        href,
                                    )
                                    val title = child.textContent.trim()
                                    if (!path.isNullOrBlank() && title.isNotBlank()) {
                                        result += NavEntry(title, path, level)
                                    }
                                }

                                "ol", "ul", "li" -> walk(child, if (child.localNameOrTag() == "li") level + 1 else level)
                            }
                        }
                    }
                    walk(nav, 0)
                    if (result.isNotEmpty()) return result
                }
            }
        }
        val ncx = manifest.values.firstOrNull {
            it.mediaType.equals("application/x-dtbncx+xml", true)
        } ?: return emptyList()
        val doc = runCatching { readXml(zip, ncx.path, "EPUB2 NCX") }.getOrNull() ?: return emptyList()
        val navMap = doc.documentElement.descendants()
            .firstOrNull { it.localNameOrTag() == "navMap" } ?: return emptyList()
        val result = mutableListOf<NavEntry>()
        fun walk(element: Element, level: Int) {
            element.childElements().forEach { child ->
                when (child.localNameOrTag().lowercase()) {
                    "navpoint" -> {
                        val label = child.descendants()
                            .firstOrNull { it.localNameOrTag() == "text" }
                            ?.textContent?.trim()
                        val src = child.descendants()
                            .firstOrNull { it.localNameOrTag() == "content" }
                            ?.attr("src")
                        val path = src?.let {
                            normalizeArchivePath(ncx.path.substringBeforeLast('/', ""), it)
                        }
                        if (!label.isNullOrBlank() && path != null) {
                            result += NavEntry(label, path, level)
                        }
                        walk(child, level + 1)
                    }

                    else -> walk(child, level)
                }
            }
        }
        walk(navMap, 0)
        return result
    }

    private fun readXml(zip: ZipFile, path: String, label: String): Document {
        val entry = zip.getEntry(path)
            ?: throw EpubParseException("缺少 $label：$path")
        if (entry.size > MAX_XML_BYTES) throw EpubParseException("$label 过大")
        val bytes = readEntry(zip, path, MAX_XML_BYTES)
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
                runCatching {
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                }
                runCatching {
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                }
                runCatching {
                    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                }
                runCatching {
                    setAttribute(
                        "http://javax.xml.XMLConstants/property/accessExternalDTD",
                        "",
                    )
                }
                runCatching {
                    setAttribute(
                        "http://javax.xml.XMLConstants/property/accessExternalSchema",
                        "",
                    )
                }
            }
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (error: Throwable) {
            throw EpubParseException("$label 解析失败", error)
        }
    }

    private fun validateEntries(zip: ZipFile) {
        var count = 0
        var total = 0L
        val names = HashSet<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            count++
            if (count > MAX_ENTRIES) throw EpubParseException("EPUB 文件条目过多")
            if (entry.isDirectory) continue
            val normalized = normalizeArchivePath("", entry.name)
                ?: throw EpubParseException("EPUB 包含非法路径")
            if (!names.add(normalized)) {
                throw EpubParseException("EPUB 包含重复的 ZIP 路径")
            }
            if (entry.size > 0) {
                total += entry.size
                if (total > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw EpubParseException("EPUB 解压后内容过大")
                }
            }
        }
    }

    private fun readEntry(zip: ZipFile, path: String, limit: Long): ByteArray {
        val entry = zip.getEntry(path) ?: throw IOException("条目不存在")
        if (entry.size > limit) throw IOException("条目过大")
        return zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                readBytes += read
                if (total > limit || readBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw IOException("条目超过解压限制")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun normalizeArchivePath(baseDir: String, href: String): String? {
        if (href.isBlank()) return null
        val withoutFragment = decodePercent(href.substringBefore('#').substringBefore('?'))
        if (withoutFragment.isBlank()) return null
        if (withoutFragment.contains('\\') ||
            withoutFragment.startsWith("/") ||
            withoutFragment.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))
        ) {
            return null
        }
        val joined = if (baseDir.isBlank()) withoutFragment else "$baseDir/$withoutFragment"
        val parts = ArrayDeque<String>()
        joined.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/").takeIf { it.isNotBlank() }
    }

    private fun decodePercent(value: String): String {
        if (!value.contains('%')) return value
        val bytes = ByteArrayOutputStream()
        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (code != null) {
                    bytes.write(code)
                    index += 3
                    continue
                }
            }
            val encoded = value[index].toString().toByteArray(StandardCharsets.UTF_8)
            bytes.write(encoded, 0, encoded.size)
            index++
        }
        return bytes.toString(StandardCharsets.UTF_8.name())
    }

    private fun isXhtml(mediaType: String, path: String): Boolean =
        mediaType.contains("xhtml", true) ||
            mediaType.contains("html", true) ||
            path.endsWith(".xhtml", true) ||
            path.endsWith(".html", true) ||
            path.endsWith(".htm", true)

    private fun detectImageType(bytes: ByteArray): ImageType? {
        if (bytes.size < 12) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() ->
                ImageType("image/jpeg", "jpg")

            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                ImageType("image/png", "png")

            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() ->
                ImageType("image/gif", "gif")

            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) ->
                ImageType("image/webp", "webp")

            else -> null
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun normalizeText(value: String): String =
        value.replace('\u00a0', ' ')
            .replace(Regex("[\\t\\r ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()

    private fun Node.descendants(): Sequence<Element> = sequence {
        val children = childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                yield(child)
                yieldAll(child.descendants())
            }
        }
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            (children.item(i) as? Element)?.let(result::add)
        }
        return result
    }

    private fun Element.blockChildren(): List<Element> =
        childElements().filterNot { child ->
            child.localNameOrTag().equals("script", true) ||
                child.localNameOrTag().equals("style", true) ||
                child.localNameOrTag().equals("nav", true)
        }

    private fun Element.localNameOrTag(): String = localName ?: tagName.substringAfter(':')

    private fun Element.attr(name: String): String {
        val direct = getAttribute(name).takeIf { it.isNotBlank() }
            ?: getAttribute(name.substringAfter(':')).takeIf { it.isNotBlank() }
        if (!direct.isNullOrBlank()) return direct
        val attributes = attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            if (attribute.localName == name.substringAfter(':') ||
                attribute.nodeName == name
            ) {
                return attribute.nodeValue ?: ""
            }
        }
        return ""
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    private data class ImageType(val mediaType: String, val extension: String)

    private companion object {
        const val MAX_ENTRIES = 5_000
        const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 250L * 1024 * 1024
        const val MAX_RESOURCE_BYTES = 30L * 1024 * 1024
        const val MAX_XML_BYTES = 8L * 1024 * 1024
        const val MAX_TEXT_CHARS = 64 * 1024 * 1024
    }
}
