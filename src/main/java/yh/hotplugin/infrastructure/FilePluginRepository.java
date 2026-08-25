package yh.hotplugin.infrastructure;

import yh.hotplugin.domain.model.Plugin;
import yh.hotplugin.domain.model.PluginStatus;
import yh.hotplugin.domain.repository.PluginRepository;
import yh.hotplugin.infrastructure.dto.PluginJsonDto;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugin repository with JSON file persistence.
 * All operations read from and write to the JSON file directly.
 */
public final class FilePluginRepository implements PluginRepository {

    private final Path jsonFile;
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\"name\"\\s*:\\s*\"([^\"]*)\"[^,]*,\\s*" +
                    "\"jarPath\"\\s*:\\s*\"([^\"]*)\"[^,]*,\\s*" +
                    "\"status\"\\s*:\\s*\"([^\"]*)\""
    );

    public FilePluginRepository(String jsonFilePath) {
        this.jsonFile = Paths.get(jsonFilePath);
    }

    @Override
    public Plugin find(String name) {
        Map<String, Plugin> plugins = loadFromJson();
        return plugins.get(name);
    }

    @Override
    public Collection<Plugin> findAll() {
        return new ArrayList<>(loadFromJson().values());
    }

    @Override
    public void save(Plugin plugin) {
        Map<String, Plugin> plugins = loadFromJson();
        plugins.put(plugin.getName(), plugin);
        persist(plugins);
    }

    @Override
    public void remove(String name) {
        Map<String, Plugin> plugins = loadFromJson();
        plugins.remove(name);
        persist(plugins);
    }

    /* ---- JSON persistence ---- */

    private void persist(Map<String, Plugin> plugins) {
        try {
            String json = toJson(plugins.values());
            Files.createDirectories(jsonFile.getParent());
            Files.write(jsonFile, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist plugin list to " + jsonFile, e);
        }
    }

    private Map<String, Plugin> loadFromJson() {
        Map<String, Plugin> plugins = new ConcurrentHashMap<>();
        if (!Files.exists(jsonFile)) {
            return plugins;
        }
        try {
            String content = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
            List<PluginJsonDto> dtoList = fromJson(content);
            for (PluginJsonDto dto : dtoList) {
                Plugin plugin = dto.toPlugin();
                if (plugin.getJarPath() != null &&
                        new File(plugin.getJarPath()).isFile()) {
                    plugins.put(plugin.getName(), plugin);
                }
            }
        } catch (IOException e) {
            System.err.println("WARN: Failed to load plugin list from " + jsonFile + ": " + e.getMessage());
        }
        return plugins;
    }

    /* ---- Simple JSON serializer (zero-dependency) ---- */

    static String toJson(Collection<Plugin> plugins) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        boolean first = true;
        for (Plugin p : plugins) {
            if (!first) sb.append(",\n");
            sb.append("  {");
            sb.append("\"name\":\"").append(escapeJson(p.getName())).append("\",");
            sb.append("\"jarPath\":\"").append(escapeJson(p.getJarPath())).append("\",");
            sb.append("\"status\":\"").append(p.getStatus().name()).append("\"");
            sb.append("}");
            first = false;
        }
        sb.append("\n]");
        return sb.toString();
    }

    static List<PluginJsonDto> fromJson(String json) {
        List<PluginJsonDto> result = new ArrayList<>();
        Matcher matcher = ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            result.add(new PluginJsonDto(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return result;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}