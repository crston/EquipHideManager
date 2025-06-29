package com.gmail.bobason01;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class EquipHideManager extends JavaPlugin implements Listener {
    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager();

        // ENTITY_EQUIPMENT 패킷: 다른 플레이어 장비 숨김
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> original =
                        event.getPacket().getSlotStackPairLists().read(0);
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> modified = new ArrayList<>();
                for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : original) {
                    EnumWrappers.ItemSlot slot = pair.getFirst();
                    ItemStack item = pair.getSecond();
                    if (isArmorSlot(slot) && isTargetArmor(item.getType())) {
                        modified.add(new Pair<>(slot, new ItemStack(Material.AIR)));
                    } else {
                        modified.add(pair);
                    }
                }
                event.getPacket().getSlotStackPairLists().write(0, modified);
            }
        });

        // SET_SLOT 패킷: 단일 슬롯 갱신 시 장비 숨김
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int windowId = event.getPacket().getIntegers().read(0);
                int slot = event.getPacket().getIntegers().read(1);
                boolean isOwnInventory = event.getPlayer().getOpenInventory().getTopInventory()
                        .equals(event.getPlayer().getInventory());
                if (windowId == 0 && isOwnInventory && isArmorInventorySlot(slot)) {
                    ItemStack item = event.getPacket().getItemModifier().read(0);
                    if (isTargetArmor(item.getType())) {
                        event.getPacket().getItemModifier().write(0, new ItemStack(Material.AIR));
                    }
                }
            }
        });

        // WINDOW_ITEMS 패킷: 전체 인벤토리 갱신 시 장비 숨김
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int windowId = event.getPacket().getIntegers().read(0);
                boolean isOwnInventory = event.getPlayer().getOpenInventory().getTopInventory()
                        .equals(event.getPlayer().getInventory());
                if (windowId == 0 && isOwnInventory) {
                    List<ItemStack> items = event.getPacket().getItemListModifier().read(0);
                    if (items.size() >= 39) {
                        List<ItemStack> modified = new ArrayList<>(items);
                        for (int i = 5; i <= 8; i++) {
                            ItemStack item = items.get(i);
                            if (isTargetArmor(item.getType())) {
                                modified.set(i, new ItemStack(Material.AIR));
                            }
                        }
                        event.getPacket().getItemListModifier().write(0, modified);
                    }
                }
            }
        });

        // 피해/공격 이벤트 감지용 Listener 등록
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("[EquipHideManager] Plugin Enabled");
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
        getLogger().info("[EquipHideManager] Plugin Disabled");
    }

    private boolean isTargetArmor(Material material) {
        return material.name().endsWith("_HELMET") ||
                material.name().endsWith("_CHESTPLATE") ||
                material.name().endsWith("_LEGGINGS") ||
                material.name().endsWith("_BOOTS");
    }

    private boolean isArmorInventorySlot(int slot) {
        return slot >= 5 && slot <= 8;
    }

    private boolean isArmorSlot(EnumWrappers.ItemSlot slot) {
        return slot == EnumWrappers.ItemSlot.HEAD ||
                slot == EnumWrappers.ItemSlot.CHEST ||
                slot == EnumWrappers.ItemSlot.LEGS ||
                slot == EnumWrappers.ItemSlot.FEET;
    }

    // 공격자 또는 피해자 장비를 강제로 숨김
    public void refreshEquipmentHide(Player player) {
        var packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().write(0, player.getEntityId());

        List<Pair<EnumWrappers.ItemSlot, ItemStack>> hidden = new ArrayList<>();
        hidden.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, new ItemStack(Material.AIR)));
        hidden.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, new ItemStack(Material.AIR)));
        hidden.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, new ItemStack(Material.AIR)));
        hidden.add(new Pair<>(EnumWrappers.ItemSlot.FEET, new ItemStack(Material.AIR)));

        packet.getSlotStackPairLists().write(0, hidden);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && viewer.canSee(player)) {
                try {
                    protocolManager.sendServerPacket(viewer, packet);
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Failed to send hidden equipment packet to player: " + viewer.getName(), e);
                }
            }
        }
    }

    // 피해 또는 공격 시 장비 숨기기
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            refreshEquipmentHide(victim);
        }

        if (event.getDamager() instanceof Player attacker) {
            refreshEquipmentHide(attacker);
        }
    }
}
