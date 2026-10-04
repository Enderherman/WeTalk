package top.enderherman.wetalk.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FreshDatabaseInitializationTest {
    @Test
    @SuppressWarnings("unchecked")
    void freshMysqlRunsOnlyTheCurrentSchemaAndNotExistingDatabaseMigrations() throws Exception {
        Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("compose.infra.yaml")));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> mysql = (Map<String, Object>) services.get("mysql");
        List<String> mounts = (List<String>) mysql.get("volumes");
        List<String> initialization = mounts.stream().filter(value -> value.contains("/docker-entrypoint-initdb.d")).toList();
        assertEquals(1, initialization.size());
        String[] mount = initialization.get(0).split(":");
        Path source = Path.of(mount[0]);
        assertTrue(Files.isRegularFile(source), "Mount the schema file; mounting sql/ also executes non-idempotent upgrades");
        String sql = Files.readString(source);
        assertTrue(sql.contains("CREATE TABLE `chat_message`"));
        assertTrue(sql.contains("`client_message_id`"));
        assertTrue(sql.contains("`last_read_message_id`"));
        assertFalse(sql.contains("ADD COLUMN"));
    }
}
