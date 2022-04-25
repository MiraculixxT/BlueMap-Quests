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

import java.util.Map.Entry;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.plugin.Plugin;

public abstract class AreaProvider {

    private static AreaProvider loaded;

    static {
        final Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuard != null) {
            final String wgVersion = worldGuard.getDescription().getVersion();
            try {
                final String packageName = AreaProvider.class.getPackage().getName();
                if (wgVersion.startsWith("6") || wgVersion.startsWith("5")) {
                    loaded = (AreaProvider) Class.forName(packageName + ".AreaProvider_WG6").newInstance();
                } else {
                    loaded = (AreaProvider) Class.forName(packageName + ".AreaProvider_WG7").newInstance();
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException exception) {
                Bukkit.getLogger().log(Level.SEVERE, "BlueMapQuests could not find a valid AreaProvider implementation.");
            }
        }
    }

    abstract Entry<double[], double[]> get(ProtectedRegion region);

    abstract double getLowerY(ProtectedRegion region);

    abstract double getUpperY(ProtectedRegion region);

    public static Entry<double[], double[]> getArea(ProtectedRegion region) {
        return loaded.get(region);
    }

    public static double getMinY(ProtectedRegion region) {
        return loaded.getLowerY(region);
    }

    public static double getMaxY(ProtectedRegion region) {
        return loaded.getUpperY(region);
    }
}
