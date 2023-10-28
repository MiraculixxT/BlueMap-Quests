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
import de.bluecolored.bluemap.api.marker.DistanceRangedMarker;
import de.bluecolored.bluemap.api.marker.ExtrudeMarker;
import de.bluecolored.bluemap.api.marker.Marker;
import de.bluecolored.bluemap.api.marker.MarkerAPI;
import de.bluecolored.bluemap.api.marker.MarkerSet;
import de.bluecolored.bluemap.api.marker.POIMarker;
import de.bluecolored.bluemap.api.marker.Shape;
import de.bluecolored.bluemap.api.marker.ShapeMarker;
import io.github.znetworkw.znpcservers.npc.NPC;
import lol.pyr.znpcsplus.ZNPCsPlus;
import me.pikamug.quests.BukkitQuestsPlugin;
import me.pikamug.quests.Quests;
import me.pikamug.quests.dependencies.BukkitDependencies;
import me.pikamug.quests.dependencies.reflect.worldguard.WorldGuardAPI;
import me.pikamug.quests.quests.Quest;
import me.pikamug.quests.quests.components.Stage;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.npc.NPCRegistry;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BlueMapQuests extends JavaPlugin {
    @SuppressWarnings("unused")
    public static String uid = "%%__USER__%% | %%__RESOURCE__%% | %%__NONCE__%%";

    private Plugin blueMap;
    private CitizensPlugin citizens;
    private ZNPCsPlus znpcs;
    private static WorldGuardAPI worldGuardApi = null;
    private NPCRegistry registry;
    private Quests quests;

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
    MarkerAPI markerApi;

    private FileConfiguration cfg;
    private MarkerSet set;
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
                znpcs = depends.getZnpcsPlus();
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
                markerApi = api.getMarkerAPI();
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
                if (markerApi.getMarkerSet(cfg.getString("label.name")).isPresent()) {
                    set = markerApi.getMarkerSet(cfg.getString("label.name")).get();
                } else {
                    set = markerApi.createMarkerSet(cfg.getString("label.name", "Quests"));
                }
                if (set == null) {
                    getLogger().severe("Error creating marker set");
                    return;
                }
                set.setDefaultHidden(cfg.getBoolean("layer.hide-by-default", false));

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
                cirFillOpacity =  (int) (cfg.getDouble("circle.fill-style.opacity", 0.35) * 255);
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

                markerApi.save();

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
        return api.createImage(ImageIO.read(stream), iconName);
    }

    private class UpdateJob implements Runnable {
        @Override
        public void run() {
            BlueMapAPI.getInstance().ifPresent(api -> {
                if (set != null) {
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

                    try {
                        markerApi.save();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        public void npcMarker(UUID uuid, String labelPrefix, String icon) {
            Entity entity;
            Location l = null;
            String id = null;
            String name = null;
            if (citizens != null) {
                final net.citizensnpcs.api.npc.NPC n = registry.getByUniqueId(uuid);
                if (n != null) {
                    entity = n.getEntity();
                    l = n.getStoredLocation();
                    if (l == null && entity != null) {
                        l = entity.getLocation();
                    }
                    id = "quests-npc-" + + n.getId();
                    name = n.getFullName();
                }
            }
            if (znpcs != null) {
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
            if (l != null && l.getWorld() != null) {
                if (set.getMarker(id).isPresent()) {
                    final Marker m = set.getMarker(id).get();
                    final String label = m.getLabel();
                    if (!label.contains(labelPrefix)) {
                        m.setLabel(label.replace("NPC:", "/ " + labelPrefix + " NPC:"));
                    }
                    m.setPosition(new Vector3d(l.getX(), l.getY(), l.getZ()));
                } else {
                    if (BlueMapAPI.getInstance().isPresent()) {
                        final Location finalLoc = l;
                        final String finalName = name;
                        final String finalId = id;
                        BlueMapAPI.getInstance().get().getWorld(l.getWorld().getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {
                            final Vector3d warpMarkerPos = new Vector3d(finalLoc.getX(), finalLoc.getY(), finalLoc.getZ());
                            final POIMarker warpMarker = set.createPOIMarker(finalId, map, warpMarkerPos);
                            warpMarker.setLabel("Quest " + labelPrefix + " NPC: " + ChatColor.stripColor(finalName));
                            final Vector2i iconAnchor = warpMarker.getAnchor();
                            if (iconAnchor != null) {
                                warpMarker.setIcon(icon, iconAnchor);
                            }
                            defineDistances(warpMarker, minimumDistance, maximumDistance);
                        }));
                    }
                }
            }
        }

        public void cirMarker(Location l, double radius, String name, String labelPrefix) {
            final String id = "quests-loc-" + name + "-" + l.getX() + "-" + l.getY() + "-" + l.getZ();
            if (BlueMapAPI.getInstance().isPresent() && l.getWorld() != null) {
                BlueMapAPI.getInstance().get().getWorld(l.getWorld().getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {
                    if (set.getMarker(id).isPresent()) {
                        final ShapeMarker sm = (ShapeMarker) set.getMarker(id).get();
                        sm.setLabel("Quest " + labelPrefix + ": " + name);
                        final Color lineColor = new Color(cirLineColor.getRed(), cirLineColor.getGreen(),
                                cirLineColor.getBlue(), cirLineOpacity);
                        sm.setLineColor(lineColor);
                        sm.setLineWidth(cirLineWeight);
                        final Color fillColor = new Color(cirFillColor.getRed(), cirFillColor.getGreen(),
                                cirFillColor.getBlue(), cirFillOpacity);
                        sm.setFillColor(fillColor);
                        defineDistances(sm, minimumDistance, maximumDistance);
                    } else {
                        final Shape circle = Shape.createCircle(l.getX(), l.getZ(), radius, 16);
                        set.createShapeMarker(id, map, circle, (float) l.getY());
                    }
                }));
            }
        }

        public void areaMarker(ProtectedRegion pr, String labelPrefix, World world) {
            final String id = "quests-reg-" + pr.getId();
            if (BlueMapAPI.getInstance().isPresent() && world != null) {
                BlueMapAPI.getInstance().get().getWorld(world.getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {
                    if (set.getMarker(id).isPresent()) {
                        final ExtrudeMarker em = (ExtrudeMarker) set.getMarker(id).get();
                        em.setLabel("Quest " + labelPrefix + ": " + pr.getId());
                        final Color lineColor = new Color(areaLineColor.getRed(), areaLineColor.getGreen(),
                                areaLineColor.getBlue(), areaLineOpacity);
                        em.setLineColor(lineColor);
                        em.setLineWidth(areaLineWeight);
                        final Color fillColor = new Color(areaFillColor.getRed(), areaFillColor.getGreen(),
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
                        set.createExtrudeMarker(id, map, _points, new Shape(points),
                                (float) AreaProvider.getMinY(pr), (float) AreaProvider.getMaxY(pr));
                    }
                }));
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
        @EventHandler(priority= EventPriority.MONITOR)
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
