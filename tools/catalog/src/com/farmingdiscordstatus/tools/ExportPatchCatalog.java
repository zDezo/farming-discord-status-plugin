package com.farmingdiscordstatus.tools;

import com.google.gson.GsonBuilder;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Offline maintenance tool, NEVER packaged or run by the plugin. Reads the
 * pinned dependency's decoder into a static data resource. Reflection is confined
 * to this explicit developer task because RuneLite keeps these data types private.
 */
public final class ExportPatchCatalog
{
    private static final String FARMING = "net.runelite.client.plugins.timetracking.farming.";
    private static final String HUNTER = "net.runelite.client.plugins.timetracking.hunter.";

    private static Object call(Object object, String method) throws Exception
    {
        Method getter = object.getClass().getMethod(method);
        getter.setAccessible(true);
        return getter.invoke(object);
    }

    public static void main(String[] args) throws Exception
    {
        Set<String> supported = new HashSet<>(Arrays.asList("HERB", "TREE", "FRUIT_TREE", "ALLOTMENT",
            "FLOWER", "BUSH", "HOPS", "GRAPE", "MUSHROOM", "ANIMA", "BELLADONNA", "CACTUS",
            "CORAL", "HESPORI", "SEAWEED", "COMPOST"));
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("source", "https://github.com/runelite/runelite/tree/runelite-parent-" + args[1]
            + "/runelite-client/src/main/java/net/runelite/client/plugins/timetracking");
        List<Map<String, Object>> patches = new ArrayList<>();
        Set<Object> implementations = new LinkedHashSet<>();
        Constructor<?> constructor = Class.forName(FARMING + "FarmingWorld").getDeclaredConstructor();
        constructor.setAccessible(true);
        Map<?, ?> tabs = (Map<?, ?>) call(constructor.newInstance(), "getTabs");
        for (Object group : tabs.values())
        {
            for (Object patch : (Set<?>) group)
            {
                Object implementation = call(patch, "getImplementation");
                String category = ((Enum<?>) call(implementation, "getTab")).name();
                if (category.equals("SPECIAL")) category = ((Enum<?>) implementation).name();
                if (category.equals("BIG_COMPOST")) category = "COMPOST";
                if (!supported.contains(category)) continue;
                String type = ((Enum<?>) implementation).name();
                Object region = call(patch, "getRegion");
                Object regionId = call(region, "getRegionID");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", regionId + "." + call(patch, "getVarbit"));
                row.put("category", category);
                row.put("locationId", "region-" + regionId + "-" + type.toLowerCase(Locale.ROOT));
                String label = (String) call(region, "getName");
                if ((category.equals("TREE") || category.equals("FRUIT_TREE")) && !category.equals(type))
                    label += " (" + type.substring(0, 1) + type.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ') + ")";
                row.put("location", label);
                row.put("implementation", type);
                patches.add(row);
                implementations.add(implementation);
            }
        }
        catalog.put("patches", patches);
        Map<String, Object> decoders = new LinkedHashMap<>();
        Method decode = Class.forName(FARMING + "PatchImplementation").getDeclaredMethod("forVarbitValue", int.class);
        decode.setAccessible(true);
        for (Object implementation : implementations)
        {
            List<List<Object>> values = new ArrayList<>();
            for (int value = 0; value < 256; value++)
            {
                Object state = decode.invoke(implementation, value);
                if (state == null) { values.add(null); continue; }
                String produce = ((Enum<?>) call(state, "getProduce")).name();
                String cropState = produce.equals("WEEDS") || produce.equals("SCARECROW")
                    ? "EMPTY" : ((Enum<?>) call(state, "getCropState")).name();
                Method tickRate = state.getClass().getDeclaredMethod("getTickRate");
                Method stages = state.getClass().getDeclaredMethod("getStages");
                tickRate.setAccessible(true);
                stages.setAccessible(true);
                values.add(Arrays.asList(cropState, tickRate.invoke(state),
                    (int) stages.invoke(state) - 1 - (int) call(state, "getStage")));
            }
            decoders.put(((Enum<?>) implementation).name(), values);
        }
        catalog.put("decoders", decoders);
        List<Map<String, Object>> birdhouses = new ArrayList<>();
        for (Object space : Class.forName(HUNTER + "BirdHouseSpace").getEnumConstants())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", "birdhouse." + call(space, "getVarp"));
            row.put("locationId", "birdhouse-" + ((Enum<?>) space).name().toLowerCase(Locale.ROOT));
            row.put("location", call(space, "getName"));
            row.put("maxValue", Class.forName(HUNTER + "BirdHouse").getEnumConstants().length * 3);
            Field duration = Class.forName(HUNTER + "BirdHouseTracker").getDeclaredField("BIRD_HOUSE_DURATION");
            duration.setAccessible(true);
            row.put("duration", duration.getInt(null));
            birdhouses.add(row);
        }
        catalog.put("birdhouses", birdhouses);
        Path target = Path.of(args[0]);
        Files.createDirectories(target.getParent());
        String json = new GsonBuilder().disableHtmlEscaping().create().toJson(catalog)
            .replace("},{", "},\n{").replace("],[", "],\n[");
        Files.writeString(target, json + "\n", StandardCharsets.UTF_8);
        System.out.println("Exported " + patches.size() + " patches and " + decoders.size() + " decoders from " + args[1]);
    }
}
