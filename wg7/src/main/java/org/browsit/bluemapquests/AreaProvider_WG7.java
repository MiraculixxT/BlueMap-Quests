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

import java.util.AbstractMap;
import java.util.List;
import java.util.Map.Entry;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;

public class AreaProvider_WG7 extends AreaProvider {
    @Override
    Entry<double[], double[]> get(ProtectedRegion region) {
        RegionType tn = region.getType();
        BlockVector3 p0 = region.getMinimumPoint();
        BlockVector3 p1 = region.getMaximumPoint();

        double[] x = null;
        double[] z = null;
        if(tn == RegionType.CUBOID) {
            x = new double[4];
            z = new double[4];
            x[0] = p0.getX(); z[0] = p0.getZ();
            x[1] = p0.getX(); z[1] = p1.getZ()+1.0;
            x[2] = p1.getX() + 1.0; z[2] = p1.getZ()+1.0;
            x[3] = p1.getX() + 1.0; z[3] = p0.getZ();
        } else if(tn == RegionType.POLYGON) {
            ProtectedPolygonalRegion ppr = (ProtectedPolygonalRegion)region;
            List<BlockVector2> points = ppr.getPoints();
            x = new double[points.size()];
            z = new double[points.size()];
            for(int i = 0; i < points.size(); i++) {
                BlockVector2 pt = points.get(i);
                x[i] = pt.getX(); z[i] = pt.getZ();
            }
        } else {
            // Unsupported type
            return null;
        }
        return new AbstractMap.SimpleEntry<double[], double[]>(x, z);
    }

    @Override
    double getLowerY(ProtectedRegion region) {
        return region.getMinimumPoint().getY();
    }

    @Override
    double getUpperY(ProtectedRegion region) {
        return region.getMaximumPoint().getY() + 1;
    }
}
