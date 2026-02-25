package com.sqlaudit.parser;

import com.sqlaudit.model.SqlFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MyBatisMapperParserTest {

    @TempDir
    Path tempDir;

    private final MyBatisMapperParser parser = new MyBatisMapperParser();

    @Test
    void shouldCorrectlyParseSelectStatement() throws Exception {
        File file = tempDir.resolve("TestMapper.xml").toFile();
        String content = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.TestMapper">
                    <select id="selectUser">
                        SELECT * FROM users WHERE id = #{id}
                    </select>
                </mapper>
                """;
        Files.writeString(file.toPath(), content);

        List<SqlFragment> fragments = parser.parse(file, tempDir);

        assertEquals(1, fragments.size());
        SqlFragment fragment = fragments.get(0);
        assertEquals("selectUser", fragment.getStatementId());
        assertEquals("select", fragment.getStatementType());
        assertEquals("SELECT * FROM users WHERE id = ?", fragment.getSqlText());
    }

    @Test
    void shouldCleanSqlCommentsAndEntities() throws Exception {
        File file = tempDir.resolve("ComplexMapper.xml").toFile();
        String content = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.ComplexMapper">
                    <update id="updateSomething">
                        UPDATE table 
                        SET col = 1 
                        -- checking comment removal
                        WHERE a &gt; 10 AND b &lt; 5
                        /* multi-line
                           comment */
                    </update>
                </mapper>
                """;
        Files.writeString(file.toPath(), content);

        List<SqlFragment> fragments = parser.parse(file, tempDir);

        assertEquals(1, fragments.size());
        String sql = fragments.get(0).getSqlText();
        
        assertFalse(sql.contains("--"));
        assertFalse(sql.contains("/*"));
        assertTrue(sql.contains("WHERE a > 10 AND b < 5"));
        assertEquals("UPDATE table SET col = 1 WHERE a > 10 AND b < 5", sql);
    }

    @Test
    void shouldHandleIncludeTags() throws Exception {
        File file = tempDir.resolve("IncludeMapper.xml").toFile();
        String content = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="com.example.IncludeMapper">
                    <sql id="cols">id, name</sql>
                    <select id="selectWithInclude">
                        SELECT <include refid="cols"/> FROM table
                    </select>
                </mapper>
                """;
        Files.writeString(file.toPath(), content);

        List<SqlFragment> fragments = parser.parse(file, tempDir);

        assertEquals(1, fragments.size());
        assertEquals("SELECT id, name FROM table", fragments.get(0).getSqlText());
    }

    @Test
    void shouldIgnoreNonMapperXml() throws Exception {
        File file = tempDir.resolve("config.xml").toFile();
        String content = "<configuration><property>value</property></configuration>";
        Files.writeString(file.toPath(), content);

        assertFalse(parser.isMyBatisMapper(file));
        List<SqlFragment> fragments = parser.parse(file, tempDir);
        assertTrue(fragments.isEmpty());
    }
}
