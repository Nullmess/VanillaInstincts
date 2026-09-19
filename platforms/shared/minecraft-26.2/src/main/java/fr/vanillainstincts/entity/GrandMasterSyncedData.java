package fr.vanillainstincts.entity;

/** Client/server synchronized Grand Master marker for villager rendering. */
public interface GrandMasterSyncedData {
    byte GRAND_MASTER_OFF_EVENT = 125;
    byte GRAND_MASTER_ON_EVENT = 126;

    boolean vanillainstincts$isGrandMasterSynced();

    void vanillainstincts$setGrandMasterSynced(boolean value);
}
