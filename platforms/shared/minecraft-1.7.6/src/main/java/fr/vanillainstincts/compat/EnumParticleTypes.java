package fr.vanillainstincts.compat;

/** Post-1.8 particle names mapped to the string identifiers used by 1.7.x. */
public enum EnumParticleTypes {
    CLOUD("cloud"), CRIT("crit"), ENCHANTMENT_TABLE("enchantmenttable"),
    EXPLOSION_NORMAL("explode"), FLAME("flame"), PORTAL("portal"),
    SMOKE_NORMAL("smoke"), VILLAGER_ANGRY("angryVillager"),
    VILLAGER_HAPPY("happyVillager"), WATER_SPLASH("splash");
    private final String id;
    EnumParticleTypes(String id){ this.id=id; }
    public String id(){ return id; }
}
