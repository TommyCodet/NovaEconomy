package de.txmmy.novaeconomy.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(component(path, placeholders, true));
    }

    public Component component(String path, Map<String, String> placeholders, boolean prefix) {
        String raw = messages.getString(path, "<red>Fehlende Nachricht: " + path + "</red>");
        if (prefix) {
            raw = messages.getString("prefix", "") + raw;
        }
        return miniMessage.deserialize(raw, resolver(placeholders));
    }

    public Component raw(String miniMessageText, Map<String, String> placeholders) {
        return miniMessage.deserialize(miniMessageText, resolver(placeholders));
    }

    public List<Component> componentList(String path, Map<String, String> placeholders) {
        List<Component> components = new ArrayList<>();
        for (String line : messages.getStringList(path)) {
            components.add(raw(line, placeholders));
        }
        return components;
    }

    private TagResolver resolver(Map<String, String> placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        placeholders.forEach((key, value) -> builder.resolver(Placeholder.unparsed(key, value)));
        return builder.build();
    }
}
