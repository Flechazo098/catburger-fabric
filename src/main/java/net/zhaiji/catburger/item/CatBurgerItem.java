package net.zhaiji.catburger.item;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.zhaiji.catburger.CatBurger;
import net.zhaiji.catburger.attachment.ModAttachmentType;
import net.zhaiji.catburger.config.CatBurgerConfig;
import net.zhaiji.catburger.init.InitItem;
import net.zhaiji.catburger.network.CatBurgerPacket;

import java.util.List;

public class CatBurgerItem extends TrinketItem {

    public CatBurgerItem() {
        super(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CatBurger.MOD_ID, "cat_burger"))));

        registerEventHandlers();
    }

    private void registerEventHandlers() {

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                handlePlayerWakeUp(newPlayer);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                checkAndRestoreFood(player);
            }
        });
    }

    private void checkAndRestoreFood(Player player) {
        if (player.tickCount % CatBurgerConfig.get().trinket_cooldown != 0) return;

        boolean hasCatBurger = TrinketsApi.getTrinketComponent(player)
                .map(component -> component.isEquipped(InitItem.CAT_BURGER))
                .orElse(false);

        if (hasCatBurger) {
            FoodData foodData = player.getFoodData();
            int foodLevel = foodData.getFoodLevel();
            if (foodLevel < CatBurgerConfig.get().food_max_restoration) {
                foodData.setFoodLevel(Math.min(foodLevel + CatBurgerConfig.get().food_restoration_form_trinket,
                        CatBurgerConfig.get().food_max_restoration));
            }
        }
    }

    @Override
    public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
    }

    @Override
    public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        return true;
    }


    @Override
    public void appendHoverText(
            ItemStack itemStack,
            TooltipContext tooltipContext,
            List<Component> list,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
        list.add(Component.translatable("item.catburger.cat_burger.tooltip"));

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        int cooldown = CatBurgerConfig.get().totem_cooldown;
        if (cooldown <= 0) return;

        long lastUseTime = player.getAttachedOrElse(
                ModAttachmentType.CAT_BURGER_TOTEM_COOLDOWN,
                0L
        );

        long now = player.level().getGameTime();
        long remainingTicks = cooldown - (now - lastUseTime);

        if (remainingTicks > 0) {
            double seconds = remainingTicks / 20.0;
            list.add(Component.translatable("item.catburger.cat_burger.cooldown.remaining", String.format("%.1f", seconds)));
        } else {
            list.add(Component.translatable("item.catburger.cat_burger.cooldown.ready"));
        }
    }


    public static boolean handlePlayerDeath(Player player, DamageSource source) {
        if (!CatBurgerConfig.get().totem_effect_active) return false;

        long gameTime = player.level().getGameTime();
        long lastUseTime = player.getAttachedOrElse(ModAttachmentType.CAT_BURGER_TOTEM_COOLDOWN, 0L);
        int cooldown = CatBurgerConfig.get().totem_cooldown;
        if (lastUseTime > 0L && gameTime - lastUseTime < cooldown) {
            return false;
        }

        return TrinketsApi.getTrinketComponent(player)
                .map(component -> {
                    if (component.isEquipped(InitItem.CAT_BURGER)) {
                        player.setHealth(CatBurgerConfig.get().health_restoration_form_totem);
                        player.getFoodData().setFoodLevel(CatBurgerConfig.get().food_restoration_form_totem);
                        player.getFoodData().setSaturation(CatBurgerConfig.get().saturation_restoration_form_totem);
                        player.setAttached(ModAttachmentType.CAT_BURGER_TOTEM_COOLDOWN, gameTime);
                        player.level().broadcastEntityEvent(player, (byte) 35);
                        if (player instanceof ServerPlayer serverPlayer) {
                            CatBurgerPacket.sendToClient(serverPlayer);
                        }
                        return true;
                    }
                    return false;
                }).orElse(false);
    }

    public static void handlePlayerWakeUp(Player player) {
        if (!CatBurgerConfig.get().wake_up_can_reset_cooldown) return;

        TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
            if (component.isEquipped(InitItem.CAT_BURGER)) {
                player.setAttached(ModAttachmentType.CAT_BURGER_TOTEM_COOLDOWN, 0L);
            }
        });
    }

}