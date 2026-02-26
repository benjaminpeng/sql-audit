package com.sqlaudit.parser;

import com.sqlaudit.model.SqlFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MyBatis XML Mapper Parser.
 * Extracts select/insert/update/delete SQL statements from XML files.
 * Refactored for Clean Code and Java 21.
 */
@Component
public class MyBatisMapperParser {

    private static final Logger log = LoggerFactory.getLogger(MyBatisMapperParser.class);

    private static final Set<String> SQL_TAGS = Set.of("select", "insert", "update", "delete");
    private static final Pattern MYBATIS_PARAM_PATTERN = Pattern.compile("#\\{[^}]*}");
    private static final Pattern DYNAMIC_TAG_PATTERN = Pattern.compile(
            "<(?:if|choose|when|otherwise|where|set|trim|foreach|bind)[^>]*>|</(?:if|choose|when|otherwise|where|set|trim|foreach|bind)>");
    private static final Pattern INCLUDE_TAG_PATTERN = Pattern
            .compile("<include\\s+refid=\"[^\"]*\"\\s*/?>|</include>");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Checks if a file is a valid MyBatis Mapper XML.
     */
    public boolean isMyBatisMapper(File file) {
        if (!file.getName().endsWith(".xml")) {
            return false;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            int headSize = Math.min(bytes.length, 8192);
            String head = new String(bytes, 0, headSize, StandardCharsets.ISO_8859_1);
            return head.contains("<mapper") || head.contains("<!DOCTYPE mapper");
        } catch (IOException e) {
            log.warn("Unable to read file: {}", file.getAbsolutePath(), e);
        }
        return false;
    }

    /**
     * Parses a MyBatis Mapper XML file and extracts SQL fragments.
     */
    public List<SqlFragment> parse(File file, Path repoRoot) {
        var fragments = new ArrayList<SqlFragment>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security (XXE prevention)
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            var builder = factory.newDocumentBuilder();
            var document = builder.parse(file);
            document.getDocumentElement().normalize();

            var root = document.getDocumentElement();
            if (!"mapper".equals(root.getTagName())) {
                return fragments;
            }

            String namespace = root.getAttribute("namespace");

            // Collect <sql> fragments for <include> resolution
            var sqlFragmentMap = collectSqlFragments(root);

            // Extract SQL statements
            for (String tag : SQL_TAGS) {
                var nodes = root.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    var element = (Element) nodes.item(i);
                    String id = element.getAttribute("id");
                    String rawSql = extractSqlText(element, sqlFragmentMap);
                    String cleanedSql = cleanSql(rawSql);

                    if (!cleanedSql.isBlank()) {
                        int lineNumber = estimateLineNumber(file, id);
                        fragments.add(SqlFragment.builder()
                                .filePath(file.getAbsolutePath())
                                .relativePath(repoRoot.relativize(file.toPath()).toString())
                                .statementId(id)
                                .statementType(tag)
                                .sqlText(cleanedSql)
                                .lineNumber(lineNumber)
                                .namespace(namespace)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse MyBatis XML: {}", file.getAbsolutePath(), e);
        }
        return fragments;
    }

    /**
     * Collects reusable <sql> fragments.
     */
    private Map<String, String> collectSqlFragments(Element root) {
        var fragments = new HashMap<String, String>();
        var sqlNodes = root.getElementsByTagName("sql");
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            var sqlElement = (Element) sqlNodes.item(i);
            fragments.put(sqlElement.getAttribute("id"), sqlElement.getTextContent());
        }
        return fragments;
    }

    /**
     * Recursively extracts SQL text from an element.
     */
    private String extractSqlText(Element element, Map<String, String> sqlFragmentMap) {
        var sb = new StringBuilder();
        var children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);

            // Use switch pattern matching on node type (if available) or standard switch
            Short nodeType = child.getNodeType();

            switch (nodeType) {
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                    sb.append(child.getTextContent());

                case Node.ELEMENT_NODE -> {
                    var childElement = (Element) child;
                    if ("include".equals(childElement.getTagName())) {
                        String refId = childElement.getAttribute("refid");
                        sb.append(" ").append(sqlFragmentMap.getOrDefault(refId, "")).append(" ");
                    } else {
                        // Recursively handle dynamic tags (if, where, trim, etc.)
                        sb.append(" ").append(extractSqlText(childElement, sqlFragmentMap)).append(" ");
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Cleans and normalizes SQL text.
     */
    private String cleanSql(String rawSql) {
        var sql = removeComments(rawSql);
        sql = replacePlaceholders(sql);
        sql = removeDynamicTags(sql);
        sql = unescapeXml(sql);
        return normalizeWhitespace(sql);
    }

    private String removeComments(String sql) {
        // Remove single-line comments
        var noLineComments = sql.replaceAll("--.*", " ");
        // Remove block comments (using (?s) for DOTALL mode to match newlines)
        return noLineComments.replaceAll("(?s)/\\*.*?\\*/", " ");
    }

    private String replacePlaceholders(String sql) {
        // Replace #{param} with ?
        return MYBATIS_PARAM_PATTERN.matcher(sql).replaceAll("?");
    }

    private String removeDynamicTags(String sql) {
        // Clean leftover dynamic tags and include tags
        var cleaned = DYNAMIC_TAG_PATTERN.matcher(sql).replaceAll(" ");
        return INCLUDE_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
    }

    private String unescapeXml(String sql) {
        return sql.replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("&amp;", "&");
    }

    private String normalizeWhitespace(String sql) {
        return MULTI_SPACE_PATTERN.matcher(sql).replaceAll(" ").trim();
    }

    /**
     * Estimates line number by searching for the statement ID in the file.
     */
    private int estimateLineNumber(File file, String statementId) {
        if (statementId == null || statementId.isEmpty()) {
            return 1;
        }
        String token = "id=\"" + statementId + "\"";
        try {
            // ISO_8859_1 做 1:1 字节映射，便于在未知编码文件中按 ASCII 令牌查找并统计行号。
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
            try (var reader = new BufferedReader(new StringReader(content))) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    if (line.contains(token)) {
                        return lineNum;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Unable to determine line number for: {}", file.getAbsolutePath());
        }
        return 1;
    }
}
