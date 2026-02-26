package com.sqlaudit.util;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文本解码工具：优先 UTF-8，失败时回退到常见中文编码，避免 Windows/WSL 场景乱码。
 */
public final class TextDecodingUtils {

    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Charset GBK = Charset.forName("GBK");

    private TextDecodingUtils() {
    }

    public static DecodedText decodeBestEffort(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new DecodedText("", StandardCharsets.UTF_8.name(), false, false);
        }

        // UTF-8 BOM
        if (hasPrefix(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new DecodedText(
                    new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8),
                    "UTF-8 (BOM)",
                    false,
                    true);
        }

        // UTF-16 BOM
        if (hasPrefix(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new DecodedText(
                    new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE),
                    "UTF-16LE (BOM)",
                    false,
                    true);
        }
        if (hasPrefix(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new DecodedText(
                    new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE),
                    "UTF-16BE (BOM)",
                    false,
                    true);
        }

        String utf8 = tryStrictDecode(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return new DecodedText(utf8, StandardCharsets.UTF_8.name(), false, false);
        }

        for (Charset charset : List.of(GB18030, GBK)) {
            String decoded = tryStrictDecode(bytes, charset);
            if (decoded != null) {
                return new DecodedText(decoded, charset.name(), true, false);
            }
        }

        // 最后兜底，避免直接失败
        return new DecodedText(new String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8.name(), true, false);
    }

    private static String tryStrictDecode(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasPrefix(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    public record DecodedText(String text, String charsetName, boolean usedFallbackCharset, boolean hadBom) {
        public String buildNotice(String fileName) {
            if (!usedFallbackCharset && !hadBom) {
                return null;
            }
            if (usedFallbackCharset) {
                return "检测到 " + fileName + " 可能不是 UTF-8 编码，已自动按 " + charsetName + " 解码。";
            }
            if (hadBom && !charsetName.startsWith("UTF-8")) {
                return "检测到 " + fileName + " 使用 " + charsetName + " 编码，已自动处理。";
            }
            return null;
        }
    }
}
