package dev.antifullbright;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Item> DARK_MINING_LIGHT_SOURCES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AntiFullbright.MOD_ID, "dark_mining_light_sources"));
    public static final TagKey<Block> DARK_MINING_COUNTED_BLOCKS = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(AntiFullbright.MOD_ID, "dark_mining_counted_blocks"));

    private ModTags() {}
}
