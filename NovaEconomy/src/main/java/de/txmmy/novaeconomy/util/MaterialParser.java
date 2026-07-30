package de.txmmy.novaeconomy.util;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

public final class MaterialParser {
    private MaterialParser() {
    }

    public static Optional<Material> parseBlock(String input) {
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(normalized);
        if (material == null || !material.isBlock() || !material.isItem() || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(material);
    }
}
