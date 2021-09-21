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
import me.blackvein.quests.Quest;
import me.blackvein.quests.Quests;
import me.blackvein.quests.Stage;
import me.blackvein.quests.reflect.worldguard.WorldGuardAPI;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
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

public class BlueMapQuests extends JavaPlugin {
    private Plugin blueMap;
    private CitizensPlugin citizens;
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
        try {
            if (pm.getPlugin("Citizens") != null) {
                if (!pm.getPlugin("Citizens").isEnabled()) {
                    getLogger().warning("Citizens was detected, but is not enabled! Fix it to allow linkage.");
                } else {
                    citizens = (CitizensPlugin) pm.getPlugin("Citizens");
                    if (citizens != null) {
                        registry = citizens.getNPCRegistry();
                    }
                }
            }
        } catch (Exception e) {
            getLogger().warning("Legacy version of Citizens found. Linkage with Dynmap-Quests not enabled.");
        }
        if (pm.getPlugin("WorldGuard") != null) {
            if (!pm.getPlugin("WorldGuard").isEnabled()) {
                getLogger().warning("WorldGuard was detected, but is not enabled! Fix it to allow linkage.");
            } else {
                worldGuardApi = new WorldGuardAPI(pm.getPlugin("WorldGuard"));
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
                cirFillOpacity =  (int) (cfg.getDouble("circle.fill-style.opacity", 0.35) * 255);;
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

                getServer().getScheduler().scheduleSyncDelayedTask(this, new UpdateJob(), 40);
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

                    for (Quest q : quests.getLoadedQuests()) {
                        if (citizens != null && q.getNpcStart() != null) {
                            npcMarker(q.getNpcStart(), prefixStart, startIcon);
                        }
                        for (Stage s : q.getStages()) {
                            int killIndex = 0;
                            for (Location l : s.getLocationsToKillWithin()) {
                                int radius = s.getRadiiToKillWithin().get(killIndex);
                                String name = s.getKillNames().get(killIndex);
                                cirMarker(l, radius, name, prefixKillArea);
                                killIndex++;
                            }
                            int reachIndex = 0;
                            for (Location l : s.getLocationsToReach()) {
                                int radius = s.getRadiiToReachWithin().get(reachIndex);
                                String name = s.getLocationNames().get(reachIndex);
                                cirMarker(l, radius, name, prefixReachArea);
                                reachIndex++;
                            }
                            if (citizens != null) {
                                for (Integer i : s.getCitizensToInteract()) {
                                    npcMarker(registry.getById(i), prefixInteract, interactIcon);
                                }
                                for (Integer i : s.getCitizensToKill()) {
                                    npcMarker(registry.getById(i), prefixKill, killIcon);
                                }
                                for (Integer i : s.getItemDeliveryTargets()) {
                                    npcMarker(registry.getById(i), prefixDelivery, deliveryIcon);
                                }
                            }
                            if (worldGuardApi != null) {
                                if (q.getRegionStart() != null) {
                                    String r = q.getRegionStart();
                                    for (World world : getServer().getWorlds()) {
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

        public void npcMarker(NPC n, String labelPrefix, String icon) {
            if (n != null) {
                Location l = n.getStoredLocation();
                if (l == null) {
                    l = n.getEntity().getLocation();
                }
                String id = "quests-npc-" + + n.getId();
                Marker m = null;
                if (set.getMarker(id).isPresent()) {
                    m = set.getMarker(id).get();
                }
                if (m != null) {
                    // Marker already created using another quest objective
                    String label = m.getLabel();
                    if (!label.contains(labelPrefix)) {
                        m.setLabel(label.replace("NPC:", "/ " + labelPrefix + " NPC:"));
                    }
                } else {
                    final Location finalLoc = l;
                    if (BlueMapAPI.getInstance().isPresent() && l.getWorld() != null) {
                        BlueMapAPI.getInstance().get().getWorld(l.getWorld().getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {

                            final Vector3d warpMarkerPos = new Vector3d(finalLoc.getX(), finalLoc.getY(), finalLoc.getZ());
                            final POIMarker warpMarker = set.createPOIMarker(id, map, warpMarkerPos);
                            warpMarker.setLabel("Quest " + labelPrefix + " NPC: " + ChatColor.stripColor(n.getFullName()));
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
                    final Shape circle = Shape.createCircle(l.getX(), l.getZ(), radius, 16);
                    final ShapeMarker sm = set.createShapeMarker(id, map, circle, (float) l.getY());
                    if (sm != null) {
                        sm.setLabel("Quest " + labelPrefix + ": " + name);
                        final Color lineColor = new Color(cirLineColor.getRed(), cirLineColor.getGreen(),
                                cirLineColor.getBlue(), cirLineOpacity);
                        sm.setLineColor(lineColor);
                        sm.setLineWidth(cirLineWeight);
                        final Color fillColor = new Color(cirFillColor.getRed(), cirFillColor.getGreen(),
                                cirFillColor.getBlue(), cirFillOpacity);
                        sm.setFillColor(fillColor);
                        defineDistances(sm, minimumDistance, maximumDistance);
                    }
                }));
            }
        }

        public void areaMarker(ProtectedRegion pr, String labelPrefix, World world) {
            final String id = "quests-reg-" + pr.getId();
            if (BlueMapAPI.getInstance().isPresent() && world != null) {
                BlueMapAPI.getInstance().get().getWorld(world.getUID()).ifPresent(blueWorld -> blueWorld.getMaps().forEach(map -> {
                    final Map.Entry<double[], double[]> area = AreaProvider.getArea(pr);
                    final Vector2d[] points = new Vector2d[area.getKey().length];
                    for (int i = 0; i < area.getKey().length; i++) {
                        points[i] = new Vector2d(area.getKey()[i], area.getValue()[i]);
                    }

                    final Vector3d _points = new Vector3d(points[0].getX(), renderHeight, points[0].getY());
                    final ExtrudeMarker em = set.createExtrudeMarker(id, map, _points, new Shape(points),
                            (float) AreaProvider.getMinY(pr), (float) AreaProvider.getMaxY(pr));
                    if (em != null) {
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
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if (name.equals("BlueMap") || name.equals("Quests") || name.equals("Citizens")
                    || name.equals("WorldGuard")) {
                if (blueMap.isEnabled() && quests.isEnabled()) {
                    activate();
                }
            }
        }
    }
}
