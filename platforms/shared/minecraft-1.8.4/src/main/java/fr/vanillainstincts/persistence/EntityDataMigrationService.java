package fr.vanillainstincts.persistence;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;

/** Migre les anciennes données et assainit les états d'entité incohérents. */
public final class EntityDataMigrationService {
    private static final String SCHEMA_KEY =
            "vanillainstincts_entity_schema";
    private static final int DATA_VERSION = 1;

    private static final String CARTOGRAPHER_STAGE =
            "vanillainstincts_cartographer_stage";
    private static final String PRODUCTION_UNTIL =
            "vanillainstincts_production_active_until";
    private static final String CLERIC_STAND =
            "vanillainstincts_cleric_brew_stand";

    private EntityDataMigrationService() {
    }

    public static boolean migrate(Entity entity, long gameTime) {
        if (entity == null) {
            return false;
        }
        NBTTagCompound persistent = entity.getEntityData();
        int storedVersion = persistent.getInteger(SCHEMA_KEY);
        boolean schemaUpgrade = storedVersion < DATA_VERSION;

        // Les données peuvent être injectées ou devenir incohérentes après
        // l'événement de chargement initial. Les migrations sûres et
        // l'assainissement doivent donc rester réexécutables.
        boolean changed = migratePrefix(persistent, "mobmind_",
                "vanillainstincts_");
        changed |= migratePrefix(persistent, "civitasvivens_",
                "vanillainstincts_");
        changed |= clampTemporalValues(persistent);
        if (entity instanceof EntityVillager) {
            changed |= sanitizeVillagerActivities(persistent, gameTime);
        }
        if (schemaUpgrade) {
            persistent.setInteger(SCHEMA_KEY, DATA_VERSION);
        }
        return schemaUpgrade || changed;
    }

    private static boolean migratePrefix(NBTTagCompound persistent,
                                         String oldPrefix,
                                         String newPrefix) {
        boolean changed = false;
        for (String key : new ArrayList<>(persistent.getKeySet())) {
            if (!key.startsWith(oldPrefix)) {
                continue;
            }
            String replacement = newPrefix
                    + key.substring(oldPrefix.length());
            NBTBase value = persistent.getTag(key);
            if (!persistent.hasKey(replacement) && value != null) {
                persistent.setTag(replacement, value.copy());
            }
            persistent.removeTag(key);
            changed = true;
        }
        return changed;
    }

    private static boolean clampTemporalValues(NBTTagCompound persistent) {
        boolean changed = false;
        for (String key : fr.vanillainstincts.compat.LegacyJava8.copyList(persistent.getKeySet())) {
            if (!key.startsWith("vanillainstincts_")
                    || !persistent.hasKey(key, 4)
                    || !temporalKey(key)) {
                continue;
            }
            long value = persistent.getLong(key);
            if (value < 0L) {
                persistent.setLong(key, 0L);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean temporalKey(String key) {
        return key.endsWith("_at") || key.endsWith("_until")
                || key.endsWith("_ready") || key.endsWith("_since");
    }

    private static boolean sanitizeVillagerActivities(NBTTagCompound tag,
                                                       long gameTime) {
        boolean changed = false;
        int cartographerStage = tag.getInteger(CARTOGRAPHER_STAGE);
        if (cartographerStage < 0 || cartographerStage > 3) {
            changed |= remove(tag, CARTOGRAPHER_STAGE,
                    "vanillainstincts_cartographer_destination",
                    "vanillainstincts_cartographer_return",
                    "vanillainstincts_cartographer_stage_at");
        }

        if (tag.hasKey(PRODUCTION_UNTIL, 4)
                && (!tag.hasKey("vanillainstincts_production_product_id",
                8)
                || !tag.hasKey("vanillainstincts_production_job_pos",
                4))) {
            changed |= remove(tag, PRODUCTION_UNTIL,
                    "vanillainstincts_production_result_at",
                    "vanillainstincts_production_product_id",
                    "vanillainstincts_production_product_count",
                    "vanillainstincts_production_job_pos",
                    "vanillainstincts_production_completed");
        }

        if (tag.hasKey(CLERIC_STAND, 4)
                && !tag.hasKey("vanillainstincts_cleric_brew_started_at",
                4)) {
            changed |= remove(tag, CLERIC_STAND,
                    "vanillainstincts_cleric_brew_started_at",
                    "vanillainstincts_cleric_brew_inspect_at",
                    "vanillainstincts_cleric_brew_action_at",
                    "vanillainstincts_cleric_brew_expected",
                    "vanillainstincts_cleric_brew_recipe",
                    "vanillainstincts_cleric_brew_load_step",
                    "vanillainstincts_cleric_brew_hand_clear_at");
        }

        if (tag.getLong("vanillainstincts_cartographer_ready_at")
                > gameTime + 2_400_000L) {
            tag.setLong("vanillainstincts_cartographer_ready_at", gameTime);
            changed = true;
        }
        return changed;
    }

    private static boolean remove(NBTTagCompound tag, String... keys) {
        boolean changed = false;
        for (String key : keys) {
            if (tag.hasKey(key)) {
                tag.removeTag(key);
                changed = true;
            }
        }
        return changed;
    }
}
