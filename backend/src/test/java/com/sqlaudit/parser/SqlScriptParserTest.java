package com.sqlaudit.parser;

import com.sqlaudit.model.SqlFragment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptParserTest {

    private final SqlScriptParser parser = new SqlScriptParser();

    @Test
    void shouldSplitStatementsAroundCommentsAndQuotedSemicolons() {
        String sql = """
                -- comment with ;
                SELECT ';' AS literal;
                /* block ; comment */
                UPDATE demo_user
                SET name = 'a;b'
                WHERE id = 1;
                """;

        List<SqlFragment> fragments = parser.parse(sql, "demo.sql");

        assertEquals(2, fragments.size());
        assertEquals("select", fragments.get(0).getStatementType());
        assertEquals("update", fragments.get(1).getStatementType());
        assertTrue(fragments.get(1).getSqlText().contains("name = 'a;b'"));
    }

    @Test
    void shouldKeepDoubleDollarFunctionBodyAsSingleStatement() {
        String sql = """
                CREATE FUNCTION demo_fn()
                RETURNS integer
                LANGUAGE plpgsql
                AS $$
                BEGIN
                  RETURN 1;
                END;
                $$;
                SELECT 1;
                """;

        List<SqlFragment> fragments = parser.parse(sql, "demo.sql");

        assertEquals(2, fragments.size());
        assertEquals("create", fragments.get(0).getStatementType());
        assertTrue(fragments.get(0).getSqlText().contains("RETURN 1;"));
        assertEquals("select", fragments.get(1).getStatementType());
    }

    @Test
    void shouldKeepTaggedDollarFunctionBodyAsSingleStatement() {
        String sql = """
                CREATE PROCEDURE demo_proc()
                LANGUAGE plpgsql
                AS $body$
                BEGIN
                  PERFORM 1;
                  PERFORM 2;
                END;
                $body$;
                SELECT 2;
                """;

        List<SqlFragment> fragments = parser.parse(sql, "demo.sql");

        assertEquals(2, fragments.size());
        assertEquals("create", fragments.get(0).getStatementType());
        assertTrue(fragments.get(0).getSqlText().contains("PERFORM 2;"));
        assertEquals("select", fragments.get(1).getStatementType());
    }
}
