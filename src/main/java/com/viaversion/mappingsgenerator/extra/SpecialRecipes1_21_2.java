/*
 * This file is part of ViaVersion Mappings - https://github.com/ViaVersion/Mappings
 * Copyright (C) 2023 Nassim Jahnke
 * Copyright (C) 2023-2025 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.mappingsgenerator.extra;

import com.google.gson.JsonObject;
import com.viaversion.mappingsgenerator.MappingsLoader;
import com.viaversion.mappingsgenerator.MappingsOptimizer;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntArrayTag;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;

public final class SpecialRecipes1_21_2 {

    private static final String[] SMITHING_ADDITION = {"diamond", "redstone", "emerald", "lapis_lazuli", "gold_ingot", "quartz", "netherite_ingot", "iron_ingot", "copper_ingot", "amethyst_shard"};
    private static final String[] SMITHING_TEMPLATE = {"raiser_armor_trim_smithing_template", "spire_armor_trim_smithing_template", "silence_armor_trim_smithing_template", "wild_armor_trim_smithing_template", "snout_armor_trim_smithing_template", "host_armor_trim_smithing_template", "wayfinder_armor_trim_smithing_template", "ward_armor_trim_smithing_template", "dune_armor_trim_smithing_template", "bolt_armor_trim_smithing_template", "netherite_upgrade_smithing_template", "flow_armor_trim_smithing_template", "vex_armor_trim_smithing_template", "tide_armor_trim_smithing_template", "eye_armor_trim_smithing_template", "rib_armor_trim_smithing_template", "coast_armor_trim_smithing_template", "sentry_armor_trim_smithing_template", "shaper_armor_trim_smithing_template"};
    private static final String[] FURNACE_INPUT = {"salmon", "magenta_terracotta", "oak_wood", "cobbled_deepslate", "chorus_fruit", "clay", "birch_log", "beef", "golden_chestplate", "golden_hoe", "clay_ball", "golden_pickaxe", "iron_pickaxe", "pale_oak_wood", "jungle_wood", "red_sand", "deepslate_gold_ore", "wet_sponge", "gray_terracotta", "copper_ore", "stripped_birch_log", "stripped_mangrove_log", "redstone_ore", "stripped_mangrove_wood", "sand", "stripped_spruce_log", "nether_gold_ore", "golden_leggings", "stripped_jungle_log", "ancient_debris", "deepslate_redstone_ore", "stripped_dark_oak_log", "polished_blackstone_bricks", "cherry_wood", "oak_log", "potato", "pale_oak_log", "white_terracotta", "stripped_oak_log", "golden_horse_armor", "stone_bricks", "stripped_acacia_log", "mangrove_log", "deepslate_copper_ore", "nether_bricks", "jungle_log", "golden_boots", "light_blue_terracotta", "golden_axe", "chainmail_helmet", "lime_terracotta", "stripped_pale_oak_log", "kelp", "basalt", "raw_iron", "yellow_terracotta", "iron_shovel", "iron_horse_armor", "cherry_log", "light_gray_terracotta", "blue_terracotta", "mangrove_wood", "dark_oak_log", "acacia_wood", "iron_axe", "stripped_pale_oak_wood", "deepslate_diamond_ore", "iron_leggings", "chainmail_boots", "iron_chestplate", "lapis_ore", "spruce_log", "deepslate_iron_ore", "iron_sword", "brown_terracotta", "rabbit", "stone", "cod", "raw_gold", "iron_helmet", "diamond_ore", "chainmail_leggings", "deepslate_emerald_ore", "coal_ore", "acacia_log", "black_terracotta", "deepslate_bricks", "golden_helmet", "red_terracotta", "chainmail_chestplate", "dark_oak_wood", "sandstone", "cyan_terracotta", "stripped_dark_oak_wood", "cactus", "iron_ore", "pink_terracotta", "red_sandstone", "chicken", "stripped_oak_wood", "green_terracotta", "deepslate_lapis_ore", "birch_wood", "emerald_ore", "mutton", "stripped_acacia_wood", "iron_hoe", "spruce_wood", "stripped_cherry_log", "netherrack", "stripped_jungle_wood", "golden_sword", "stripped_birch_wood", "nether_quartz_ore", "porkchop", "golden_shovel", "purple_terracotta", "stripped_spruce_wood", "iron_boots", "gold_ore", "deepslate_tiles", "cobblestone", "orange_terracotta", "quartz_block", "stripped_cherry_wood", "deepslate_coal_ore", "sea_pickle", "raw_copper"};
    private static final String[] SMITHING_BASE = {"leather_helmet", "netherite_leggings", "iron_boots", "leather_boots", "golden_leggings", "golden_chestplate", "chainmail_chestplate", "iron_leggings", "diamond_shovel", "leather_chestplate", "diamond_pickaxe", "netherite_helmet", "diamond_chestplate", "chainmail_leggings", "chainmail_boots", "diamond_leggings", "diamond_axe", "diamond_boots", "golden_boots", "diamond_hoe", "diamond_helmet", "iron_helmet", "netherite_boots", "leather_leggings", "chainmail_helmet", "netherite_chestplate", "golden_helmet", "diamond_sword", "iron_chestplate", "turtle_helmet"};
    private static final String[] SMOKER_INPUT = {"kelp", "rabbit", "potato", "cod", "salmon", "porkchop", "beef", "chicken", "mutton"};
    private static final String[] BLAST_FURNACE_INPUT = {"diamond_ore", "iron_chestplate", "nether_quartz_ore", "iron_hoe", "iron_axe", "raw_iron", "iron_shovel", "deepslate_lapis_ore", "deepslate_iron_ore", "iron_leggings", "iron_sword", "golden_pickaxe", "chainmail_leggings", "golden_boots", "golden_shovel", "chainmail_boots", "gold_ore", "coal_ore", "iron_pickaxe", "redstone_ore", "raw_gold", "golden_horse_armor", "golden_axe", "chainmail_helmet", "deepslate_copper_ore", "lapis_ore", "chainmail_chestplate", "iron_boots", "golden_leggings", "deepslate_redstone_ore", "golden_hoe", "golden_helmet", "golden_sword", "iron_horse_armor", "raw_copper", "golden_chestplate", "copper_ore", "ancient_debris", "deepslate_diamond_ore", "deepslate_gold_ore", "nether_gold_ore", "deepslate_coal_ore", "emerald_ore", "deepslate_emerald_ore", "iron_helmet", "iron_ore"};
    private static final String[] CAMPFIRE_INPUT = {"kelp", "rabbit", "potato", "cod", "salmon", "porkchop", "beef", "chicken", "mutton"};

    public static void main(final String[] args) throws IOException {
        final JsonObject mappings = MappingsLoader.load("mapping-1.21.2.json");
        final CompoundTag tag = new CompoundTag();
        final Object2IntMap<String> items = MappingsLoader.arrayToMap(mappings.getAsJsonArray("items"));
        write(tag, items, SMITHING_ADDITION, "smithing_addition");
        write(tag, items, SMITHING_TEMPLATE, "smithing_template");
        write(tag, items, FURNACE_INPUT, "furnace_input");
        write(tag, items, SMITHING_BASE, "smithing_base");
        write(tag, items, SMOKER_INPUT, "smoker_input");
        write(tag, items, BLAST_FURNACE_INPUT, "blast_furnace_input");
        write(tag, items, CAMPFIRE_INPUT, "campfire_input");
        MappingsOptimizer.write(tag, MappingsOptimizer.OUTPUT_DIR.resolve("extra/recipe-inputs-1.21.2.nbt"));
    }

    private static void write(final CompoundTag tag, final Object2IntMap<String> items, final String[] identifiers, final String key) {
        final IntArrayTag ids = new IntArrayTag(new int[identifiers.length]);
        for (int i = 0; i < identifiers.length; i++) {
            final String identifier = identifiers[i];
            final int id = items.getInt(identifier);
            if (id == -1) {
                System.err.println("Unknown item " + identifier + " :(");
                continue;
            }

            ids.set(i, id);
        }
        tag.put(key, ids);
    }
}
