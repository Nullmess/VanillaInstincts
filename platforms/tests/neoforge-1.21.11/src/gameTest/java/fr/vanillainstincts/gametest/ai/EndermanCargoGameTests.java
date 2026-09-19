package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.EndermanAdvancedController;
import fr.vanillainstincts.ai.EndermanTacticsController;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndermanCargoGameTests {
    private EndermanCargoGameTests() {
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderControlsEndermanPlatform(GameTestHelper helper) {
        Spider spider = helper.spawn(EntityType.SPIDER, new BlockPos(2, 1, 2));
        helper.assertValueEqual(EndermanTacticsController.roleFor(spider),
                EndermanCargoRole.SPIDER_PLATFORM,
                net.minecraft.network.chat.Component.literal("Une araignée transportée doit contrôler la plateforme Enderman"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonBecomesArcherPlatform(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON,
                new BlockPos(2, 1, 2));
        helper.assertValueEqual(EndermanTacticsController.roleFor(skeleton),
                EndermanCargoRole.ARCHER_PLATFORM,
                net.minecraft.network.chat.Component.literal("Le squelette doit rester un archer vanilla transporté"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperBecomesDirectDelivery(GameTestHelper helper) {
        Creeper creeper = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        helper.assertValueEqual(EndermanTacticsController.roleFor(creeper),
                EndermanCargoRole.CREEPER_DELIVERY,
                net.minecraft.network.chat.Component.literal("Le creeper doit être déposé directement puis l'Enderman doit fuir"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void healthyZombieBecomesMonsterDelivery(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE,
                new BlockPos(2, 1, 2));
        helper.assertValueEqual(EndermanTacticsController.roleFor(zombie),
                EndermanCargoRole.MONSTER_DELIVERY,
                net.minecraft.network.chat.Component.literal("Un zombie sain doit pouvoir être amené puis abandonné"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void injuredMonsterCanBeRescued(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE,
                new BlockPos(2, 1, 2));
        zombie.setHealth(1.0F);
        helper.assertValueEqual(EndermanTacticsController.roleFor(zombie),
                EndermanCargoRole.ALLY_RESCUE,
                net.minecraft.network.chat.Component.literal("Un monstre très blessé peut être extrait du combat"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void passiveAnimalBecomesCreatureGift(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        helper.assertValueEqual(EndermanTacticsController.roleFor(cow),
                EndermanCargoRole.CREATURE_GIFT,
                net.minecraft.network.chat.Component.literal("Un animal transporté doit rester une présentation neutre"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void droppedItemCanBePresented(GameTestHelper helper) {
        ItemEntity item = new ItemEntity(helper.getLevel(), 2.5D, 1.0D,
                2.5D, new ItemStack(Items.DIAMOND));
        helper.getLevel().addFreshEntity(item);
        helper.assertValueEqual(EndermanTacticsController.roleFor(item),
                EndermanCargoRole.ITEM_PRESENTATION,
                net.minecraft.network.chat.Component.literal("Un objet porté doit pouvoir être montré au joueur"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void itemEntityIsValidCargoType(GameTestHelper helper) {
        ItemEntity item = new ItemEntity(helper.getLevel(), 2.5D, 1.0D,
                2.5D, new ItemStack(Items.APPLE));
        helper.assertTrue(EndermanTacticsController.canCarryType(item),
                net.minecraft.network.chat.Component.literal("Les objets tombés doivent être des charges valides"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void endermanCannotCarryAnotherEnderman(GameTestHelper helper) {
        EnderMan enderman = helper.spawn(EntityType.ENDERMAN,
                new BlockPos(2, 1, 2));
        helper.assertFalse(EndermanTacticsController.canCarryType(enderman),
                net.minecraft.network.chat.Component.literal("Un Enderman ne doit jamais porter un autre Enderman"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void wardenCannotBeCargo(GameTestHelper helper) {
        var warden = helper.spawn(EntityType.WARDEN,
                new BlockPos(2, 1, 2));
        helper.assertFalse(EndermanTacticsController.canCarryType(warden),
                net.minecraft.network.chat.Component.literal("Le Warden doit rester exclu du transport"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void punishmentKeepsMinimumDistance(GameTestHelper helper) {
        helper.assertTrue(EndermanAdvancedController.respectsMinimumDistance(
                new Vec3(8.0D, 1.0D, 0.0D), Vec3.ZERO),
                net.minecraft.network.chat.Component.literal("La téléportation punitive doit laisser plusieurs blocs"));
        helper.succeed();
    }

    @GameTest(batch = "enderman_cargo", templateNamespace = "vanillainstincts", template = "empty")
    public static void punishmentRejectsTeleportKillDistance(GameTestHelper helper) {
        helper.assertFalse(EndermanAdvancedController.respectsMinimumDistance(
                new Vec3(1.0D, 1.0D, 1.0D), Vec3.ZERO),
                net.minecraft.network.chat.Component.literal("Le joueur ne doit pas être déposé au contact immédiat de l'Enderman"));
        helper.succeed();
    }
}
