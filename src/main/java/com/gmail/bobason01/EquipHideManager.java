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

        // 1. 다른 사람에게 방어구 안 보이게 하기
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> original = event.getPacket().getSlotStackPairLists().read(0);
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> modified = new ArrayList<>();

                for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : original) {
                    EnumWrappers.ItemSlot slot = pair.getFirst();
                    if (isArmorSlot(slot)) {
                        modified.add(new Pair<>(slot, new ItemStack(Material.AIR)));
                    } else {
                        modified.add(pair);
                    }
                }

                event.getPacket().getSlotStackPairLists().write(0, modified);
            }
        });

        // 2. 자기 자신 인벤토리에서 방어구 안 보이게 하기 (슬롯 단일 전송)
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int slot = event.getPacket().getIntegers().read(1); // 0: window id, 1: slot number
                if (isArmorInventorySlot(slot)) {
                    event.getPacket().getItemModifier().write(0, new ItemStack(Material.AIR));
                }
            }
        });

        // 3. 자기 자신 인벤토리 전체 초기화 시 방어구 안 보이게 하기
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int windowId = event.getPacket().getIntegers().read(0);
                List<ItemStack> items = event.getPacket().getItemListModifier().read(0);

                if (windowId == 0 && items.size() >= 39) { // 0 = player inventory
                    List<ItemStack> modifiedItems = new ArrayList<>(items);
                    modifiedItems.set(5, new ItemStack(Material.AIR)); // 머리
                    modifiedItems.set(6, new ItemStack(Material.AIR)); // 흉갑
                    modifiedItems.set(7, new ItemStack(Material.AIR)); // 레깅스
                    modifiedItems.set(8, new ItemStack(Material.AIR)); // 부츠
                    event.getPacket().getItemListModifier().write(0, modifiedItems);
                }
            }
        });

        getLogger().info("EquipHideManager Enabled");
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
            getLogger().info("EquipHideManager Disabled");
        }
    }

    private boolean isArmorSlot(EnumWrappers.ItemSlot slot) {
        return slot == EnumWrappers.ItemSlot.HEAD ||
                slot == EnumWrappers.ItemSlot.CHEST ||
                slot == EnumWrappers.ItemSlot.LEGS ||
                slot == EnumWrappers.ItemSlot.FEET;
    }

    private boolean isArmorInventorySlot(int slot) {
        return slot >= 5 && slot <= 8; // 머리, 흉갑, 레깅스, 부츠 슬롯
    }
}
