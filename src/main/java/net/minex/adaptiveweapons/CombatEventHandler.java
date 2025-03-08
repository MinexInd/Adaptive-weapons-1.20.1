package net.minex.adaptiveweapons;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class CombatEventHandler {
    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity && entity instanceof LivingEntity) {
                ItemStack weapon = player.getStackInHand(hand);
                onEntityHit((ServerPlayerEntity) player, (LivingEntity) entity, weapon);
            }
            return ActionResult.PASS;
        });
    }

    public static void onEntityHit(ServerPlayerEntity player, LivingEntity target, ItemStack weapon) {
        if (player == null || target == null || weapon == null) return;

        // Ensure attack is fully charged to count combos (1.9+ combat)
        if (player.getAttackCooldownProgress(0.5F) < 1.0F) {
            return; // Ignore attacks that are not fully charged
        }

        // Get or create the NBT data for tracking combo
        NbtCompound tag = weapon.getOrCreateNbt();
        long currentTime = player.getWorld().getTime();
        long lastHitTime = tag.getLong("last_hit_time");

        // Reset combo if too much time has passed (4 seconds)
        if (currentTime - lastHitTime > 80) {
            tag.putInt("combo_counter", 0);
        }

        // Increment combo count
        int combo = tag.getInt("combo_counter") + 1;
        tag.putInt("combo_counter", combo);
        tag.putLong("last_hit_time", currentTime);

        // Display action bar message
        player.sendMessage(Text.literal("§6Combo: " + combo), true);

        // Apply effects based on combo count
        applyComboEffects(player, target, weapon, combo);

        // Apply bare hand abilities if no weapon is used
        if (weapon.isEmpty()) {
            applyBareHandAbilities(player, combo);
        }
    }

    private static void applyComboEffects(ServerPlayerEntity player, LivingEntity target, ItemStack weapon, int combo) {
        World world = player.getWorld();

        // Buffs for player
        if (combo == 3) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0));
        }
        if (combo == 5) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0));
        }

        // Lightning Strike at 10 combos
        if (combo == 10 && world instanceof ServerWorld serverWorld) {
            LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
                lightning.setCosmetic(true); // Prevents it from burning things
                serverWorld.spawnEntity(lightning);
            }
        }

        // Explosion at 15 combos (safe for world)
        if (combo == 15 && world instanceof ServerWorld serverWorld) {
            serverWorld.createExplosion(null, target.getX(), target.getY(), target.getZ(), 3.0F, false, World.ExplosionSourceType.TNT);
        }

        // Strength II for 10s at 25 combos
        if (combo == 25) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 1)); // 200 ticks = 10s
        }

        // Freeze enemy at 40 combos
        if (combo == 40) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 255)); // 5 seconds
        }

        // Heal player at 60 combos
        if (combo == 60) {
            player.heal(10.0F); // 5 hearts
        }

        // Shockwave attack at 80 combos
        if (combo == 80) {
            double knockbackStrength = 2.0;
            List<Entity> nearbyEntities = world.getOtherEntities(player, player.getBoundingBox().expand(5.0));

            for (Entity entity : nearbyEntities) {
                if (entity instanceof LivingEntity && entity != player) {
                    Vec3d direction = entity.getPos().subtract(player.getPos()).normalize();
                    entity.addVelocity(direction.x * knockbackStrength, 0.5, direction.z * knockbackStrength);
                }
            }
        }

        // Ultimate move at 100 combos
        if (combo == 100) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 10));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 50));

            if (world instanceof ServerWorld serverWorld) {
                Random random = new Random();
                for (int i = 0; i < 3; i++) {
                    double offsetX = (random.nextDouble() - 0.5) * 4;
                    double offsetZ = (random.nextDouble() - 0.5) * 4;
                    BlockPos lightningPos = target.getBlockPos().add((int) offsetX, 0, (int) offsetZ);

                    if (!player.getBlockPos().isWithinDistance(lightningPos, 2)) {
                        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                        if (lightning != null) {
                            lightning.refreshPositionAfterTeleport(lightningPos.getX(), lightningPos.getY(), lightningPos.getZ());
                            serverWorld.spawnEntity(lightning);
                        }
                    }
                }

                // Play a big explosion sound effect
                serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0F, 1.0F);
            }

        }
    }

    private static void applyBareHandAbilities(ServerPlayerEntity player, int combo) {
        World world = player.getWorld();

        // Bare hand abilities with different durations based on combo
        if (combo >= 5) {
            int duration = 60 * 20; // 1 minute in ticks
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, duration, 0, false, false, true)); // Speed I
        }
        if (combo >= 10) {
            int duration = 120 * 20; // 2 minutes in ticks
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 0, false, false, true)); // Resistance I
        }
        if (combo >= 20) {
            int duration = 300 * 20; // 5 minutes in ticks
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, duration, 0, false, false, true)); // Lifesteal (Regeneration I)
        }
    }
}
