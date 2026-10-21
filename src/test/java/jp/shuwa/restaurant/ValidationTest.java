package jp.shuwa.restaurant;

import jp.shuwa.restaurant.cli.ConsoleInput;
import jp.shuwa.restaurant.db.DbConfig;
import jp.shuwa.restaurant.domain.MenuSearch;
import jp.shuwa.restaurant.domain.Rules;
import jp.shuwa.restaurant.security.AdminAuth;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @Test void inputAcceptsWhitespaceAndDeduplicatesIds() {
        assertEquals(Set.of(), ConsoleInput.ids("   "));
        assertEquals(Set.of(1,2), ConsoleInput.ids(" 1\t2 1 "));
        assertEquals(Map.of(1,2,3,1),ConsoleInput.cart("1:2  3:1"));
        for (var value : new String[]{"1:0","1:-1","1:101","1:1 1:1","1","1:1:1","1:9999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> ConsoleInput.cart(value));
        }
        for (var value : new String[]{"0","-1","1 OR 1=1","9999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> ConsoleInput.ids(value));
        }
    }

    @Test void nameAndCriteriaBoundaries() {
        for (var value : new String[]{"", " ", "bad\nname", "x".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> Rules.name(value));
        }
        assertEquals("サンプル",Rules.name(" サンプル "));
        assertThrows(IllegalArgumentException.class, () -> new MenuSearch(0,Set.of(),null,null,false,MenuSearch.Sort.ID));
        assertThrows(IllegalArgumentException.class, () -> new MenuSearch(1,Set.of(),-1,null,false,MenuSearch.Sort.ID));
        assertThrows(IllegalArgumentException.class, () -> new MenuSearch(1,Set.of(),null,-1,false,MenuSearch.Sort.ID));
    }

    @Test void boundedLineDrainsLongInputAndRecovers() throws Exception {
        var input = new ConsoleInput(new StringReader("x".repeat(5000)+"\n3\n"),new PrintWriter(new StringWriter()));
        assertThrows(IllegalArgumentException.class, () -> input.line("value"));
        assertEquals(3,input.number("number",1,5));
    }

    @Test void databaseConfigFailsClosedWithoutCredentials() {
        assertThrows(IllegalArgumentException.class, () -> DbConfig.fromEnvironment(Map.of()));
        var config = new HashMap<>(Map.of("RESTAURANT_DB_USER","app_user","RESTAURANT_DB_PASSWORD","test-only-not-a-secret"));
        assertNotNull(DbConfig.fromEnvironment(config));
        config.put("RESTAURANT_DB_USER","root");
        assertThrows(IllegalArgumentException.class, () -> DbConfig.fromEnvironment(config));
        config.put("RESTAURANT_DB_USER","app_user");
        config.put("RESTAURANT_DB_HOST","db.example.test");
        config.put("RESTAURANT_DB_TLS","DISABLED");
        assertThrows(IllegalArgumentException.class, () -> DbConfig.fromEnvironment(config));
        config.put("RESTAURANT_DB_TLS","VERIFY_IDENTITY");
        config.put("RESTAURANT_DB_HOST","localhost?allowMultiQueries=true");
        assertThrows(IllegalArgumentException.class, () -> DbConfig.fromEnvironment(config));
    }

    @Test void passwordHashesAreSaltedAndMalformedValuesFailClosed() throws Exception {
        var password = "test-only-password".toCharArray();
        var first = AdminAuth.hash(password);
        assertNotEquals(first,AdminAuth.hash(password));
        assertTrue(AdminAuth.verify(password,first));
        assertFalse(AdminAuth.verify("wrong".toCharArray(),first));
        assertFalse(AdminAuth.verify(password,null));
        assertFalse(AdminAuth.verify(password,"pbkdf2-sha256$999999999$bad$bad"));
        assertFalse(AdminAuth.verify(password,"not-a-hash"));
        assertThrows(IllegalArgumentException.class, () -> AdminAuth.hash("short".toCharArray()));
    }
}
