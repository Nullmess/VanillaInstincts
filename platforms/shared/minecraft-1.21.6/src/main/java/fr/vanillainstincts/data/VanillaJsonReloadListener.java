package fr.vanillainstincts.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Small compatibility listener for Vanilla Instincts' data-driven JSON files.
 *
 * <p>Minecraft 1.21.2 moved {@code SimpleJsonResourceReloadListener} to a
 * codec-based API and 1.21.4 changed its resource mapping constructor again.
 * Vanilla Instincts intentionally keeps its hand-validated JSON schemas, so
 * this listener isolates those loader changes without duplicating every data
 * manager per Minecraft patch.</p>
 */
abstract class VanillaJsonReloadListener
        extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    private static final String JSON_SUFFIX = ".json";
    private final String directory;

    VanillaJsonReloadListener(String directory) {
        this.directory = directory;
    }

    @Override
    protected Map<ResourceLocation, JsonElement> prepare(
            ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> loaded = new LinkedHashMap<>();
        String prefix = directory + "/";
        resourceManager.listResources(directory,
                id -> id.getPath().endsWith(JSON_SUFFIX))
                .forEach((resourceId, resource) -> loadResource(
                        loaded, prefix, resourceId, resource));
        return loaded;
    }

    private static void loadResource(
            Map<ResourceLocation, JsonElement> loaded,
            String prefix, ResourceLocation resourceId, Resource resource) {
        String path = resourceId.getPath();
        if (!path.startsWith(prefix)
                || path.length() <= prefix.length() + JSON_SUFFIX.length()) {
            return;
        }
        String logicalPath = path.substring(prefix.length(),
                path.length() - JSON_SUFFIX.length());
        ResourceLocation logicalId = ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(), logicalPath);
        try (Reader reader = resource.openAsReader()) {
            loaded.put(logicalId, JsonParser.parseReader(reader));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to load JSON resource " + resourceId, exception);
        }
    }
}
