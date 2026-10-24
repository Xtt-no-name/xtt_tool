package com.xtt.toolbox.domain.writing

import com.xtt.toolbox.domain.content.BookBlockType
import com.xtt.toolbox.domain.content.BookResource
import com.xtt.toolbox.domain.content.BookResourceKind
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 从当前内部内容模型生成合法 EPUB 3。
 *
 * 这是“新建 EPUB”导出，不是原 EPUB 高保真回写：原导入包的 XHTML/CSS/OPF
 * 没有被当前模型保留。图片会作为 EPUB 内部资源写入，正文不出现应用私有路径或资源 ID。
 */
object WritingEpubExport {
    private const val MIME_TYPE = "application/epub+zip"
    private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""
    private const val STYLE = """
body { margin: 0; padding: 0; line-height: 1.7; }
h1.chapter-title { text-align: center; font-size: 1.5em; margin: 1em 0; }
.chapter-body { white-space: pre-wrap; }
.missing-image { color: #777; }
"""
    private const val CSS_ID = "style"
    private const val NAV_ID = "nav"

    fun build(
        document: WritingDocument,
        resourceBytes: Map<String, ByteArray>,
    ): ByteArray {
        val chapters = document.document.chapters.sortedBy { it.order }
        val resourceFiles = mutableMapOf<String, String>()
        val chapterDocs = chapters.mapIndexed { index, chapter ->
            chapterDocument(document, chapter, index + 1, resourceBytes, resourceFiles)
        }
        val bookId = "urn:uuid:${UUID.randomUUID()}"
        val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val language = document.book.language?.trim()?.takeIf { it.isNotEmpty() } ?: "zh"
        val author = document.book.author?.trim()?.takeIf { it.isNotEmpty() }

        val manifest = buildString {
            appendLine("""    <item id="$NAV_ID" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            appendLine("""    <item id="$CSS_ID" href="style.css" media-type="text/css"/>""")
            chapterDocs.forEach { chapter ->
                appendLine(
                    """    <item id="chapter-${chapter.index}" href="${chapter.fileName}" media-type="application/xhtml+xml"/>""",
                )
            }
            chapterDocs.flatMap { it.images }
                .filter { it.fileName != null }
                .distinctBy { it.fileName }
                .mapIndexed { index, image ->
                    """    <item id="image-${index + 1}" href="${xml(image.fileName!!)}" media-type="${xml(image.mediaType!!)}"/>"""
                }
                .forEach { appendLine("$it") }
        }
        val spine = chapterDocs.joinToString("\n") {
            """    <itemref idref="chapter-${it.index}"/>"""
        }
        val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">${xml(bookId)}</dc:identifier>
    <dc:title>${xml(document.book.title)}</dc:title>
    <dc:language>${xml(language)}</dc:language>
${author?.let { "    <dc:creator>${xml(it)}</dc:creator>\n" }.orEmpty()}    <meta property="dcterms:modified">$modified</meta>
  </metadata>
  <manifest>
$manifest
  </manifest>
  <spine>
$spine
  </spine>
</package>
"""
        val nav = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>目录</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
<nav epub:type="toc" id="toc"><h1>目录</h1><ol>
${chapterDocs.joinToString("\n") {
            """<li><a href="${it.fileName}">${xml(it.navTitle)}</a></li>"""
        }}
</ol></nav>
</body>
</html>
"""
        return ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                writeStored(zip, "mimetype", MIME_TYPE.toByteArray(Charsets.US_ASCII))
                write(zip, "META-INF/container.xml", CONTAINER)
                write(zip, "OEBPS/content.opf", opf)
                write(zip, "OEBPS/nav.xhtml", nav)
                write(zip, "OEBPS/style.css", STYLE)
                chapterDocs.forEach { chapter ->
                    write(zip, "OEBPS/${chapter.fileName}", chapter.xhtml)
                }
                resourceFiles.forEach { (resourceId, fileName) ->
                    resourceBytes[resourceId]?.let { bytes ->
                        write(zip, "OEBPS/$fileName", bytes)
                    }
                }
            }
            buffer.toByteArray()
        }
    }

    fun suggestedFileName(bookTitle: String): String =
        WritingTxtExport.suggestedFileName(bookTitle).removeSuffix(".txt") + ".epub"

    private fun chapterDocument(
        document: WritingDocument,
        chapter: com.xtt.toolbox.data.model.Chapter,
        index: Int,
        resourceBytes: Map<String, ByteArray>,
        resourceFiles: MutableMap<String, String>,
    ): ChapterDocument {
        val raw = document.text.substring(
            chapter.startOffset.coerceIn(0, document.text.length),
            chapter.endOffset.coerceIn(0, document.text.length),
        )
        val title = chapter.title.trim()
        val bodyStart = if (title.isNotEmpty() && startsWithTitle(raw, title)) {
            raw.indexOf('\n').let { if (it < 0) raw.length else it + 1 }
        } else {
            0
        }
        val body = raw.substring(bodyStart)
        val bodyAbsolute = chapter.startOffset + bodyStart
        val images = document.document.blocks
            .filter {
                it.type == BookBlockType.IMAGE &&
                    it.startOffset >= chapter.startOffset &&
                    it.startOffset <= chapter.endOffset
            }
            .sortedBy { it.startOffset }
            .mapNotNull { block ->
                val resource = document.document.resources.firstOrNull {
                    it.resourceId == block.resourceId && it.kind == BookResourceKind.IMAGE
                } ?: return@mapNotNull null
                val bytes = resourceBytes[resource.resourceId]
                val fileName = if (bytes != null) {
                    resourceFiles.getOrPut(resource.resourceId) {
                        val extension = extensionFor(resource)
                        "images/image-${resourceFiles.size + 1}.$extension"
                    }
                } else {
                    null
                }
                ImageExport(
                    resourceId = resource.resourceId,
                    fileName = fileName,
                    mediaType = mediaTypeFor(resource).takeIf { fileName != null },
                    anchor = (block.startOffset - bodyAbsolute).coerceIn(0, body.length),
                )
            }
        val htmlBody = buildString {
            var cursor = 0
            images.forEach { image ->
                append(xml(body.substring(cursor, image.anchor)))
                append(
                    if (image.fileName != null) {
                        """<img src="${image.fileName}" alt="图片"/>"""
                    } else {
                        """<span class="missing-image">[图片]</span>"""
                    },
                )
                if (image.anchor < body.length && body[image.anchor] != '\n') append('\n')
                cursor = image.anchor
            }
            append(xml(body.substring(cursor)))
        }
        val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><meta charset="utf-8"/><title>${xml(title.ifEmpty { "第 $index 章" })}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
${title.takeIf { it.isNotEmpty() }?.let { "<h1 class=\"chapter-title\">${xml(it)}</h1>\n" }.orEmpty()}<p class="chapter-body">$htmlBody</p>
</body>
</html>
"""
        return ChapterDocument(
            index = index,
            fileName = "chapter-$index.xhtml",
            navTitle = title.ifEmpty { "第 $index 章" },
            xhtml = xhtml,
            images = images,
        )
    }

    private fun startsWithTitle(raw: String, title: String): Boolean =
        raw.startsWith(title) && (raw.length == title.length || raw[title.length] == '\n')

    private fun write(zip: ZipOutputStream, name: String, text: String) {
        write(zip, name, text.toByteArray(Charsets.UTF_8))
    }

    private fun write(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun extensionFor(resource: BookResource): String =
        when (resource.mediaType.lowercase(Locale.US)) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/svg+xml" -> "svg"
            else -> resource.relativePath.substringAfterLast('.', "bin").lowercase(Locale.US)
        }

    private fun mediaTypeFor(resource: BookResource): String =
        resource.mediaType.ifBlank { "image/png" }

    private fun xml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }

    private data class ChapterDocument(
        val index: Int,
        val fileName: String,
        val navTitle: String,
        val xhtml: String,
        val images: List<ImageExport>,
    )

    private data class ImageExport(
        val resourceId: String,
        val fileName: String?,
        val mediaType: String?,
        val anchor: Int,
    )
}
