package com.sqlaudit.parser;

import com.sqlaudit.model.SqlFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * MyBatis XML Mapper 解析器
 * 从 XML 文件中提取 select/insert/update/delete SQL 语句
 */
@Component
public class MyBatisMapperParser {

    private static final Logger log = LoggerFactory.getLogger(MyBatisMapperParser.class);

    private static final Set<String> SQL_TAGS = Set.of("select", "insert", "update", "delete");
    private static final Pattern MYBATIS_PARAM_PATTERN = Pattern.compile("#\\{[^}]*}");
    private static final Pattern MYBATIS_DOLLAR_PATTERN = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern DYNAMIC_TAG_PATTERN = Pattern.compile("<(?:if|choose|when|otherwise|where|set|trim|foreach|bind)[^>]*>|</(?:if|choose|when|otherwise|where|set|trim|foreach|bind)>");
    private static final Pattern INCLUDE_TAG_PATTERN = Pattern.compile("<include\\s+refid=\"[^\"]*\"\\s*/?>|</include>");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * 判断一个 XML 文件是否是 MyBatis Mapper 文件
     */
    public boolean isMyBatisMapper(File file) {
        if (!file.getName().endsWith(".xml")) {
            return false;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 10) {
                if (line.contains("<mapper") || line.contains("<!DOCTYPE mapper")) {
                    return true;
                }
                lineCount++;
            }
        } catch (IOException e) {
            log.warn("无法读取文件: {}", file.getAbsolutePath(), e);
        }
        return false;
    }

    /**
     * 解析一个 MyBatis Mapper XML 文件，提取所有 SQL 片段
     */
    public List<SqlFragment> parse(File file, Path repoRoot) {
        List<SqlFragment> fragments = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用外部实体（安全）
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();
            if (!"mapper".equals(root.getTagName())) {
                return fragments;
            }

            String namespace = root.getAttribute("namespace");

            // 先收集 <sql> 片段用于 include 引用
            Map<String, String> sqlFragmentMap = collectSqlFragments(root);

            // 提取 SQL 语句
            for (String tag : SQL_TAGS) {
                NodeList nodes = root.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    String id = element.getAttribute("id");
                    String rawSql = extractSqlText(element, sqlFragmentMap);
                    String cleanedSql = cleanSql(rawSql);

                    if (!cleanedSql.isBlank()) {
                        // 计算行号
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
            log.error("解析 MyBatis XML 文件失败: {}", file.getAbsolutePath(), e);
        }
        return fragments;
    }

    /**
     * 收集 <sql> 片段
     */
    private Map<String, String> collectSqlFragments(Element root) {
        Map<String, String> fragments = new HashMap<>();
        NodeList sqlNodes = root.getElementsByTagName("sql");
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element sqlElement = (Element) sqlNodes.item(i);
            String id = sqlElement.getAttribute("id");
            String content = sqlElement.getTextContent();
            fragments.put(id, content);
        }
        return fragments;
    }

    /**
     * 提取元素中的 SQL 文本（递归处理子元素）
     */
    private String extractSqlText(Element element, Map<String, String> sqlFragmentMap) {
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getTextContent());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("include".equals(childElement.getTagName())) {
                    String refId = childElement.getAttribute("refid");
                    String fragment = sqlFragmentMap.getOrDefault(refId, "");
                    sb.append(" ").append(fragment).append(" ");
                } else {
                    // 动态标签（if, where, foreach 等），递归提取内容
                    sb.append(" ").append(extractSqlText(childElement, sqlFragmentMap)).append(" ");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 清理 SQL 文本
     * - 去除 SQL 注释
     * - 替换 #{param} 为占位符
     * - 保留 ${param} 用于注入检查
     * - 处理 XML 转义符
     * - 去除多余空白
     */
    private String cleanSql(String rawSql) {
        String sql = rawSql;
        // 去除 SQL 单行注释
        sql = sql.replaceAll("--.*", " ");
        // 去除 SQL 块注释
        sql = sql.replaceAll("/\\*.*?\\*/", " ");
        // 替换 #{} 参数占位符为 ?
        sql = MYBATIS_PARAM_PATTERN.matcher(sql).replaceAll("?");
        // 去除残留的动态 XML 标签（理论上已被递归处理，这里做兜底）
        sql = DYNAMIC_TAG_PATTERN.matcher(sql).replaceAll(" ");
        sql = INCLUDE_TAG_PATTERN.matcher(sql).replaceAll(" ");
        // XML 转义符处理
        sql = sql.replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
        // 规范化空白
        sql = MULTI_SPACE_PATTERN.matcher(sql).replaceAll(" ").trim();
        return sql;
    }

    /**
     * 估算 SQL 语句在文件中的行号（通过搜索 id 属性）
     */
    private int estimateLineNumber(File file, String statementId) {
        if (statementId == null || statementId.isEmpty()) {
            return 1;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.contains("id=\"" + statementId + "\"")) {
                    return lineNum;
                }
            }
        } catch (IOException e) {
            log.warn("无法读取文件行号: {}", file.getAbsolutePath());
        }
        return 1;
    }
}
