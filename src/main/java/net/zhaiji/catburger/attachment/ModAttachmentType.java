package net.zhaiji.catburger.attachment;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.zhaiji.catburger.CatBurger;

@SuppressWarnings("UnstableApiUsage")
public class ModAttachmentType {
    public static final AttachmentType<Long> CAT_BURGER_TOTEM_COOLDOWN = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(CatBurger.MOD_ID, "cat_burger_totem_cooldown"),
            builder -> builder
                    .initializer(() -> 0L)
                    .persistent(Codec.LONG)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
    );
}
