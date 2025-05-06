package com.gmail.bobason01;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class EquipHideManager extends JavaPlugin {

    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> original = event.getPacket().getSlotStackPairLists().read(0);
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> modified = new ArrayList<>();

                for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : original) {
                    EnumWrappers.ItemSlot slot = pair.getFirst();
                    ItemStack item = pair.getSecond();
                    if (slot == EnumWrappers.ItemSlot.CHEST && item.getType() == Material.ELYTRA) {
                        modified.add(pair);
                    } else if (isArmorSlot(slot)) {
                        modified.add(new Pair<>(slot, new ItemStack(Material.AIR)));
                    } else {
                        modified.add(pair);
                    }
                }

                event.getPacket().getSlotStackPairLists().write(0, modified);
            }
        });
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int slot = event.getPacket().getIntegers().read(1);

                if (isArmorInventorySlot(slot) && slot != 6) {
                    event.getPacket().getItemModifier().write(0, new ItemStack(Material.AIR));
                }
            }
        });
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int windowId = event.getPacket().getIntegers().read(0);
                List<ItemStack> items = event.getPacket().getItemListModifier().read(0);

                if (windowId == 0 && items.size() >= 39) {
                    List<ItemStack> modified = new ArrayList<>(items);
                    modified.set(5, new ItemStack(Material.AIR)); // 머리
                    if (items.get(6).getType() != Material.ELYTRA) {
                        modified.set(6, new ItemStack(Material.AIR)); // 흉갑 (겉날개는 유지)
                    }
                    modified.set(7, new ItemStack(Material.AIR)); // 다리
                    modified.set(8, new ItemStack(Material.AIR)); // 신발

                    event.getPacket().getItemListModifier().write(0, modified);
                }
            }
        });

        getLogger().info("EquipHideManager enabled");
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
        getLogger().info("EquipHideManager disabled");
    }

    private boolean isArmorSlot(EnumWrappers.ItemSlot slot) {
        return slot == EnumWrappers.ItemSlot.HEAD ||
                slot == EnumWrappers.ItemSlot.CHEST ||
                slot == EnumWrappers.ItemSlot.LEGS ||
                slot == EnumWrappers.ItemSlot.FEET;
    }

    private boolean isArmorInventorySlot(int slot) {
        return slot >= 5 && slot <= 8;
    }
}
