/*
 * Copyright (c) 2021 Browsit, LLC. All rights reserved.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
 * NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.browsit.bluemapquests;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.*;
import io.github.znetworkw.znpcservers.npc.NPC;
import lol.pyr.znpcsplus.ZNPCsPlus;
import lol.pyr.znpcsplus.api.NpcApi;
import lol.pyr.znpcsplus.api.entity.EntityProperty;
import lol.pyr.znpcsplus.api.npc.Npc;
import lol.pyr.znpcsplus.api.npc.NpcEntry;
import me.pikamug.quests.BukkitQuestsPlugin;
import me.pikamug.quests.Quests;
import me.pikamug.quests.dependencies.BukkitDependencies;
import me.pikamug.quests.dependencies.reflect.worldguard.WorldGuardAPI;
import me.pikamug.quests.quests.Quest;
import me.pikamug.quests.quests.components.Stage;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;

public class BlueMapQuests extends JavaPlugin {
    @SuppressWarnings("unused")
    public static String uid = "%%__USER__%% | %%__RESOURCE__%% | %%__NONCE__%%";

    private Plugin blueMap;
    private CitizensPlugin citizens;
    private ZNPCsPlus znpcsLegacy;
    private NpcApi znpcs;
    private static WorldGuardAPI worldGuardApi = null;
    private NPCRegistry registry;
    private Quests quests;

    private String setId;
    private String startIcon;
    private String interactIcon;
    private String killIcon;
    private String deliveryIcon;
    private int areaFillOpacity;
    private Color areaFillColor;
    private int areaLineWeight;
    private int areaLineOpacity;
    private Color areaLineColor;
    private int cirFillOpacity;
    private Color cirFillColor;
    private int cirLineWeight;
    private int cirLineOpacity;
    private Color cirLineColor;
    private String prefixStart;
    private String prefixKillArea;
    private String prefixReachArea;
    private String prefixInteract;
    private String prefixKill;
    private String prefixDelivery;
    private String prefixWgRegion;
    private int renderHeight;
    private int minimumDistance;
    private int maximumDistance;
    BlueMapAPI markerApi;

    private FileConfiguration cfg;
    private HashMap<UUID, List<MarkerSet>> sets;
    private boolean reload = false;

    @Override
    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();

        if (pm.getPlugin("BlueMap") != null) {
            if (!pm.getPlugin("BlueMap").isEnabled()) {
                getLogger().warning("BlueMap was detected, but is not enabled! Fix it to allow linkage.");
                return;
            } else {
                blueMap = pm.getPlugin("BlueMap");
            }
        }
        if (pm.getPlugin("Quests") != null) {
            if (!pm.getPlugin("Quests").isEnabled()) {
                getLogger().warning("Quests was detected, but is not enabled! Fix it to allow linkage.");
                return;
            } else {
                quests = (Quests) pm.getPlugin("Quests");
            }
        }
        if (quests != null) {
            final BukkitDependencies depends = (BukkitDependencies) quests.getDependencies();
            if (depends.getCitizens() != null) {
                citizens = depends.getCitizens();
                if (citizens != null) {
                    registry = citizens.getNPCRegistry();
                }
            }
            if (depends.getZnpcsPlus() != null) {
                znpcsLegacy = depends.getZnpcsPlus();
            }
            if (depends.getZnpcsPlusApi() != null) {
                znpcs = depends.getZnpcsPlusApi();
            }
            if (depends.getWorldGuardApi() != null) {
                worldGuardApi = depends.getWorldGuardApi();
            }
        }

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);
        if (blueMap.isEnabled() && quests.isEnabled()) {
            activate();
        }
    }

    private void activate() {
        BlueMapAPI.onEnable(api -> {
            try {
                // Get markers API
                markerApi = api;
                if (markerApi == null) {
                    getLogger().severe("Error loading BlueMap marker API!");
                    return;
                }
                // Load configuration
                if (reload) {
                    this.reloadConfig();
                } else {
                    reload = true;
                }
                cfg = getConfig();
                cfg.options().copyDefaults(true);
                this.saveConfig();

                // Add marker set (make it transient)
                setId = "bluemap-quests.set";
                sets = new HashMap<>();
                Bukkit.getWorlds().forEach(world -> {
                    Optional<BlueMapWorld> blueWorldOptional = api.getWorld(world);
                    if (blueWorldOptional.isPresent()) {
                        BlueMapWorld blueWorld = blueWorldOptional.get();
                        List<MarkerSet> worldSets = new ArrayList<>();

                        blueWorld.getMaps().forEach(map -> {
                            MarkerSet set = map.getMarkerSets().get(cfg.getString("label.name"));

                            if (set == null) {
                                // Load marker set
                                File markerFile = new File(getDataFolder(), world.getName() + ".json");
                                if (markerFile.exists()) {
                                    try (FileReader reader = new FileReader(markerFile)) {
                                        set = MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
                                    } catch (IOException ex) {
                                        // handle io-exception
                                        ex.printStackTrace();
                                        set = MarkerSet.builder().label(cfg.getString("label.name", "Quests")).build();
                                    }
                                } else set = MarkerSet.builder().label(cfg.getString("label.name", "Quests")).build();
                                map.getMarkerSets().put(setId, set);
                            }
                            set.setDefaultHidden(cfg.getBoolean("layer.hide-by-default", false));

                            worldSets.add(set);
                        });
                        sets.put(world.getUID(), worldSets);
                    }
                });

                // Setup variables
                final String startPath = "icons.start-NPC";
                final String interactPath = "icons.interact-NPC";
                final String killPath = "icons.kill-NPC";
                final String deliveryPath = "icons.delivery-NPC";
                startIcon = createImage("markers/" + cfg.getString(startPath) + ".png", api, startPath);
                interactIcon = createImage("markers/" + cfg.getString(interactPath) + ".png", api, interactPath);
                killIcon = createImage("markers/" + cfg.getString(killPath) + ".png", api, killPath);
                deliveryIcon = createImage("markers/" + cfg.getString(deliveryPath) + ".png", api, deliveryPath);
                areaFillOpacity = (int) (cfg.getDouble("area.fill-style.opacity", 0.35) * 255);
                areaFillColor = Color.decode(cfg.getString("area.fill-style.color", "0xFF0000"));
                areaLineWeight = cfg.getInt("area.line-style.weight", 5);
                areaLineOpacity = (int) (cfg.getDouble("area.line-style.opacity", 0.8) * 255);
                areaLineColor = Color.decode(cfg.getString("area.line-style.color", "0xFF0000"));
                cirFillOpacity = (int) (cfg.getDouble("circle.fill-style.opacity", 0.35) * 255);
                cirFillColor = Color.decode(cfg.getString("circle.fill-style.color", "0xFF9999"));
                cirLineWeight = cfg.getInt("circle.line-style.weight", 5);
                cirLineOpacity = (int) (cfg.getDouble("circle.line-style.opacity", 0.8) * 255);
                cirLineColor = Color.decode(cfg.getString("circle.line-style.color", "0xFF9999"));
                prefixStart = cfg.getString("prefixes.start", "Start");
                prefixKillArea = cfg.getString("prefixes.kill-area", "Kill Area");
                prefixReachArea = cfg.getString("prefixes.reach-area", "Reach Area");
                prefixInteract = cfg.getString("prefixes.interact", "Interact");
                prefixKill = cfg.getString("prefixes.kill", "Kill");
                prefixDelivery = cfg.getString("prefixes.delivery", "Delivery");
                prefixWgRegion = cfg.getString("prefixes.wg-region", "WG Region");
                renderHeight = cfg.getInt("render.height", 63);
                minimumDistance = cfg.getInt("render.min-distance", 10);
                maximumDistance = cfg.getInt("render.max-distance", 500);

                // Setup update job based on period
                int per = cfg.getInt("update.period", 300);
                if (per < 15) {
                    per = 15;
                }

                getServer().getScheduler().scheduleSyncRepeatingTask(this, new UpdateJob(), 40, per);
                getLogger().info("v" + this.getDescription().getVersion() + " is activated");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public String createImage(String imagePath, BlueMapAPI api, String iconName) throws IOException {
        final InputStream stream = getResource(imagePath);
        if (stream == null) {
            getLogger().severe("Invalid " + imagePath + " icon path " + imagePath);
            return null;
        }

        File target = new File(api.getWebApp().getWebRoot().toString(), "bmquests/" + iconName);
        File source = new File(imagePath);
        if (!source.exists()) {
            source.mkdirs();
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return target.toPath().toString();
    }

    private class UpdateJob implements Runnable {
        @Override
        public void run() {
            BlueMapAPI.getInstance().ifPresent(api -> {
                if (sets != null) {
                    for (final Quest q : quests.getLoadedQuests()) {
                        if (citizens != null && q.getNpcStart() != null) {
                            npcMarker(q.getNpcStart(), prefixStart, startIcon);
                        }
                        for (final Stage s : q.getStages()) {
                            int killIndex = 0;
                            for (final Object obj : s.getLocationsToKillWithin()) {
                                final Location location = (Location) obj;
                                final int radius = s.getRadiiToKillWithin().get(killIndex);
                                final String name = s.getKillNames().get(killIndex);
                                cirMarker(location, radius, name, prefixKillArea);
                                killIndex++;
                            }
                            int reachIndex = 0;
                            for (final Object obj : s.getLocationsToReach()) {
                                final Location location = (Location) obj;
                                final int radius = s.getRadiiToReachWithin().get(reachIndex);
                                final String name = s.getLocationNames().get(reachIndex);
                                cirMarker(location, radius, name, prefixReachArea);
                                reachIndex++;
                            }
                            if (citizens != null) {
                                for (final UUID i : s.getNpcsToInteract()) {
                                    npcMarker(i, prefixInteract, interactIcon);
                                }
                                for (final UUID i : s.getNpcsToKill()) {
                                    npcMarker(i, prefixKill, killIcon);
                                }
                                for (final UUID i : s.getItemDeliveryTargets()) {
                                    npcMarker(i, prefixDelivery, deliveryIcon);
                                }
                            }
                            if (worldGuardApi != null) {
                                if (q.getRegionStart() != null) {
                                    final String r = q.getRegionStart();
                                    for (final World world : getServer().getWorlds()) {
                                        try {
                                            if (worldGuardApi.getRegionManager(world) != null) {
                                                if (worldGuardApi.getRegionManager(world).hasRegion(r)) {
                                                    final ProtectedRegion pr = worldGuardApi.getRegionManager(world)
                                                        .getRegion(r);
                                                    if (pr != null) {
                                                        areaMarker(pr, prefixWgRegion, world);
                                                    }
                                                }
                                            }
                                        } catch (NoSuchMethodError e) {
                                            getLogger().severe("Unsupported version of WorldGuard");
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }

        public void npcMarker(UUID uuid, String labelPrefix, String icon) {
            Entity entity;
            Location l = null;
            String id = null;
            String name = "null";
            if (citizens != null) {
                final net.citizensnpcs.api.npc.NPC n = registry.getByUniqueId(uuid);
                if (n != null) {
                    entity = n.getEntity();
                    l = n.getStoredLocation();
                    if (l == null && entity != null) {
                        l = entity.getLocation();
                    }
                    id = "quests-npc-" + +n.getId();
                    name = n.getFullName();
                }
            }
            if (znpcsLegacy != null) {
                if (((BukkitQuestsPlugin) quests).getDependencies().getZnpcsPlusUuids().contains(uuid)) {
                    final Optional<NPC> opt = NPC.all().stream().filter(npc1 -> npc1.getUUID().equals(uuid)).findAny();
                    if (opt.isPresent()) {
                        final NPC n = opt.get();
                        l = n.getLocation();
                        id = "quests-npc-" + n.getEntityID();
                        if (n.getBukkitEntity() != null) {
                            entity = (Entity) n.getBukkitEntity();
                            if (entity.getCustomName() != null) {
                                name = entity.getCustomName();
                            } else {
                                name = n.getNpcPojo().getHologramLines().get(0);
                            }
                        }
                    }
                }
            }
            if (znpcs != null) {
                final NpcApi npcApi = ((BukkitQuestsPlugin) quests).getDependencies().getZnpcsPlusApi();
                final NpcEntry entry = npcApi.getNpcRegistry().getByUuid(uuid);
                if (entry != null) {
                    final Npc znpc = entry.getNpc();
                    l = znpc.getLocation().toBukkitLocation(znpc.getWorld());
                    id = "quests-npc-" + entry.getId();
                    EntityProperty<String> displayNameProperty = npcApi.getPropertyRegistry().getByName("display_name", String.class);
                    if (displayNameProperty != null) {
                        if (znpc.hasProperty(displayNameProperty)) {
                            name = znpc.getProperty(displayNameProperty);
                        }
                    }
                }
            }
            if (l != null && l.getWorld() != null) {
                // Copy final variables for lambda
                String finalId1 = id;
                Location finalL = l;
                String finalName1 = name;

                // Add marker to sets
                sets.get(l.getWorld().getUID()).forEach(set -> {
                    if (set.getMarkers().containsKey(finalId1)) {
                        final Marker m = set.get(finalId1);
                        final String label = m.getLabel();
                        if (!label.contains(labelPrefix)) {
                            m.setLabel(label.replace("NPC:", "/ " + labelPrefix + " NPC:"));
                        }
                        m.setPosition(new Vector3d(finalL.getX(), finalL.getY(), finalL.getZ()));
                    } else {
                        if (BlueMapAPI.getInstance().isPresent()) {
                            final Location finalLoc = finalL;
                            final String finalName = finalName1;
                            final String finalId = finalId1;
                            BlueMapAPI.getInstance().get().getWorld(finalL.getWorld().getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {
                                final Vector3d warpMarkerPos = new Vector3d(finalLoc.getX(), finalLoc.getY(), finalLoc.getZ());
                                final POIMarker warpMarker = POIMarker.builder()
                                    .label("Quest " + labelPrefix + " NPC: " + ChatColor.stripColor(finalName))
                                    .position(warpMarkerPos)
                                    .icon(icon, 0, 0)
                                    .anchor(new Vector2i(16, 16))
                                    .build();
                                set.put(finalId, warpMarker);
                                defineDistances(warpMarker, minimumDistance, maximumDistance);
                            }));
                        }
                    }
                });
            }
        }

        public void cirMarker(Location l, double radius, String name, String labelPrefix) {
            final String id = "quests-loc-" + name + "-" + l.getX() + "-" + l.getY() + "-" + l.getZ();
            if (BlueMapAPI.getInstance().isPresent() && l.getWorld() != null) {
                // Loop through all maps for this world
                sets.get(l.getWorld().getUID()).forEach(set -> {
                    if (set.getMarkers().containsKey(id)) {
                        final ShapeMarker sm = (ShapeMarker) set.get(id);
                        sm.setLabel("Quest " + labelPrefix + ": " + name);
                        final de.bluecolored.bluemap.api.math.Color lineColor = new de.bluecolored.bluemap.api.math.Color(cirLineColor.getRed(), cirLineColor.getGreen(),
                            cirLineColor.getBlue(), cirLineOpacity);
                        sm.setLineColor(lineColor);
                        sm.setLineWidth(cirLineWeight);
                        final de.bluecolored.bluemap.api.math.Color fillColor = new de.bluecolored.bluemap.api.math.Color(cirFillColor.getRed(), cirFillColor.getGreen(),
                            cirFillColor.getBlue(), cirFillOpacity);
                        sm.setFillColor(fillColor);
                        defineDistances(sm, minimumDistance, maximumDistance);
                    } else {
                        final de.bluecolored.bluemap.api.math.Shape circle = de.bluecolored.bluemap.api.math.Shape.createCircle(l.getX(), l.getZ(), radius, 16);
                        ShapeMarker marker = ShapeMarker.builder()
                            .shape(circle, (float) l.getY())
                            .build();
                        set.put(id, marker);
                    }
                });
            }
        }

        public void areaMarker(ProtectedRegion pr, String labelPrefix, World world) {
            final String id = "quests-reg-" + pr.getId();
            if (BlueMapAPI.getInstance().isPresent() && world != null) {
                // Loop through all maps for this world
                sets.get(world.getUID()).forEach(set -> {
                    if (set.getMarkers().containsKey(id)) {
                        final ExtrudeMarker em = (ExtrudeMarker) set.get(id);
                        em.setLabel("Quest " + labelPrefix + ": " + pr.getId());
                        final de.bluecolored.bluemap.api.math.Color lineColor = new de.bluecolored.bluemap.api.math.Color(areaLineColor.getRed(), areaLineColor.getGreen(),
                            areaLineColor.getBlue(), areaLineOpacity);
                        em.setLineColor(lineColor);
                        em.setLineWidth(areaLineWeight);
                        final de.bluecolored.bluemap.api.math.Color fillColor = new de.bluecolored.bluemap.api.math.Color(areaFillColor.getRed(), areaFillColor.getGreen(),
                            areaFillColor.getBlue(), areaFillOpacity);
                        em.setFillColor(fillColor);
                        em.setDepthTestEnabled(false);
                        defineDistances(em, minimumDistance, maximumDistance);
                    } else {
                        final Map.Entry<double[], double[]> area = AreaProvider.getArea(pr);
                        final Vector2d[] points = new Vector2d[area.getKey().length];
                        for (int i = 0; i < area.getKey().length; i++) {
                            points[i] = new Vector2d(area.getKey()[i], area.getValue()[i]);
                        }
                        final Vector3d _points = new Vector3d(points[0].getX(), renderHeight, points[0].getY());
                        ExtrudeMarker marker = ExtrudeMarker.builder()
                            .position(_points)
                            .shape(new de.bluecolored.bluemap.api.math.Shape.Builder().addPoints(points).build(), (float) AreaProvider.getMinY(pr), (float) AreaProvider.getMaxY(pr))
                            .build();
                        set.put(id, marker);
                    }
                });
            }
        }
    }

    private void defineDistances(DistanceRangedMarker drm, double minDistance, double maxDistance) {
        if (minDistance >= 0) {
            drm.setMinDistance(minDistance);
        }
        if (maxDistance > 0 && maxDistance > minDistance) {
            drm.setMaxDistance(maxDistance);
        }
    }

    public WorldGuardAPI getWorldGuardApi() {
        return worldGuardApi;
    }

    private class OurServerListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginEnable(PluginEnableEvent event) {
            final Plugin p = event.getPlugin();
            final String name = p.getDescription().getName();
            if (name.equals("BlueMap") || name.equals("Quests") || name.equals("Citizens")
                || name.equals("ServersNPC") || name.equals("WorldGuard")) {
                if (blueMap.isEnabled() && quests.isEnabled()) {
                    activate();
                }
            }
        }
    }
}
