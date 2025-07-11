package com.gmail.bobason01;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import me.libraryaddict.disguise.events.UndisguiseEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EquipHideManager extends JavaPlugin implements Listener {

    private ProtocolManager protocolManager;
    private static final ItemStack EMPTY_ITEM = new ItemStack(Material.AIR);

    @Override
    public void onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager();

        // ENTITY_EQUIPMENT 패킷 감지 및 필터링
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> original =
                        event.getPacket().getSlotStackPairLists().read(0);
                boolean changed = false;
                List<Pair<EnumWrappers.ItemSlot, ItemStack>> modified = new ArrayList<>(original.size());
                for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : original) {
                    EnumWrappers.ItemSlot slot = pair.getFirst();
                    ItemStack item = pair.getSecond();
                    if (isArmorSlot(slot) && isTargetArmor(item)) {
                        modified.add(new Pair<>(slot, EMPTY_ITEM));
                        changed = true;
                    } else {
                        modified.add(pair);
                    }
                }
                if (changed) {
                    event.getPacket().getSlotStackPairLists().write(0, modified);
                }
            }
        });

        // SET_SLOT 패킷
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int windowId = event.getPacket().getIntegers().read(0);
                int slot = event.getPacket().getIntegers().read(1);
                if (windowId == 0 && isArmorInventorySlot(slot)) {
                    ItemStack item = event.getPacket().getItemModifier().read(0);
                    if (isTargetArmor(item)) {
                        event.getPacket().getItemModifier().write(0, EMPTY_ITEM);
                    }
                }
            }
        });

        // WINDOW_ITEMS 패킷
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int windowId = event.getPacket().getIntegers().read(0);
                if (windowId == 0) {
                    List<ItemStack> items = event.getPacket().getItemListModifier().read(0);
                    if (items.size() >= 39) {
                        boolean changed = false;
                        List<ItemStack> modified = new ArrayList<>(items);
                        for (int i = 5; i <= 8; i++) {
                            ItemStack item = items.get(i);
                            if (isTargetArmor(item)) {
                                modified.set(i, EMPTY_ITEM);
                                changed = true;
                            }
                        }
                        if (changed) {
                            event.getPacket().getItemListModifier().write(0, modified);
                        }
                    }
                }
            }
        });

        // 이벤트 등록
        getServer().getPluginManager().registerEvents(this, this);

        // 명령어 등록
        Objects.requireNonNull(this.getCommand("hidearmor")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                refreshEquipmentHide(player);
                return true;
            }
            sender.sendMessage("플레이어만 사용할 수 있습니다");
            return false;
        });
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
    }

    private boolean isTargetArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
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

    public void refreshEquipmentHide(Player player) {
        var packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        packet.getIntegers().write(0, player.getEntityId());

        List<Pair<EnumWrappers.ItemSlot, ItemStack>> hidden = List.of(
                new Pair<>(EnumWrappers.ItemSlot.HEAD, EMPTY_ITEM),
                new Pair<>(EnumWrappers.ItemSlot.CHEST, EMPTY_ITEM),
                new Pair<>(EnumWrappers.ItemSlot.LEGS, EMPTY_ITEM),
                new Pair<>(EnumWrappers.ItemSlot.FEET, EMPTY_ITEM)
        );
        packet.getSlotStackPairLists().write(0, hidden);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player) && viewer.canSee(player)) {
                protocolManager.sendServerPacket(viewer, packet);
            }
        }
    }

    // 공격 / 피해 시 장비 숨김
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            refreshEquipmentHide(victim);
        }
        if (event.getDamager() instanceof Player attacker) {
            refreshEquipmentHide(attacker);
        }
    }

    // LibsDisguises undisguise 시
    @EventHandler
    public void onUndisguise(UndisguiseEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTaskLater(this, () -> refreshEquipmentHide(player), 1L);
        }
    }

    // ModelEngine /meg undisguise 명령어 감지
    @EventHandler
    public void onMEGUndisguiseCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (msg.equals("/meg undisguise") || msg.startsWith("/meg undisguise ")) {
            Bukkit.getScheduler().runTaskLater(this, () -> refreshEquipmentHide(event.getPlayer()), 1L);
        }
    }
}
