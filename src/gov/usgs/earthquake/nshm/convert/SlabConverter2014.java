package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkNotNull;
import static gov.usgs.earthquake.nshm.util.FaultCode.FIXED;
import static gov.usgs.earthquake.nshm.util.RateType.CUMULATIVE;
import static gov.usgs.earthquake.nshm.util.RateType.INCREMENTAL;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CEUS;
import static gov.usgs.earthquake.nshm.util.Utils.readGrid;
import static org.opensha2.eq.fault.FocalMech.NORMAL;
import static org.opensha2.eq.fault.FocalMech.REVERSE;
import static org.opensha2.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_SUB_GEOMAT_LENGTH;
import static org.opensha2.internal.Parsing.splitToDoubleList;
import static org.opensha2.internal.Parsing.Delimiter.SPACE;

import gov.usgs.earthquake.nshm.util.FaultCode;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha2.eq.fault.FocalMech;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.geo.Location;
import org.opensha2.geo.Regions;
import org.opensha2.internal.Parsing;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.io.Files;
import com.google.common.math.DoubleMath;

/*
 * Convert 2014 NSHMP slab input files to XML.
 * 
 * This class was required because the 2014 slab sources are distributed at
 * three longitude-dependent depths. It made more sense to modify copies of the
 * grid parser rather than shoehorn the handlers into the existing
 * implementation. Much of this class is not relevant to or used when handling
 * the slab-depth issue.
 * 
 * @author Peter Powers
 */
class SlabConverter2014 {

  private Logger log;

  private SlabConverter2014() {}

  static SlabConverter2014 create(Logger log) {
    SlabConverter2014 sc14 = new SlabConverter2014();
    sc14.log = checkNotNull(log);
    return sc14;
  }

  // path to binary grid files in grid source input files
  private static final String SRC_DIR = "conf";

  void convert(SourceFile sf, String dir) {

    try {
      log.info("Starting source: " + sf.name);
      SlabSourceData2014 srcDat = new SlabSourceData2014();
      srcDat.name = sf.name;
      srcDat.id = -1;
      srcDat.weight = sf.weight;

      Iterator<String> lines = sf.lineIterator();

      // grid of sites (1-30) or station list (0)
      int numSta = Parsing.readInt(lines.next(), 0);
      // skip stations or lat-lon bounds
      Iterators.advance(lines, (numSta > 0) ? numSta : 2);
      // skip site data (Vs30) and Campbell basin depth
      lines.next();
      // read rupture top data (num, [z, wt M<=6.5, wt M>6.5], ...)
      readRuptureTop(lines.next(), srcDat);
      // read focal mech weights (SS, REVERSE, NORMAL)
      readMechWeights(lines.next(), srcDat);
      // read gm lookup array parameters; delta R and R max
      readLookupArrayDat(lines.next(), srcDat);

      // read source region dimensions
      readSourceLatRange(lines.next(), srcDat);
      readSourceLonRange(lines.next(), srcDat);

      // mag data - grids always supply GR data, however, Charleston fixed
      // strike grids are also fixed mag to we convert them to SINGLE here
      srcDat.grDat = GR_Data.createForGrid(lines.next());
      if (DoubleMath.fuzzyEquals(srcDat.grDat.mMin, srcDat.grDat.mMax, 0.000001)) {
        // if mMin == mMax, populate CH_Data field
        srcDat.chDat = CH_Data.create(srcDat.grDat.mMin, 0.0, 1.0, false);
      }

      // iflt, ibmat, maxMat, Mtaper
      // iflt = 0 -> no finite faults
      // iflt = 1 -> apply finite fault corrections for M>6 assuming
      // random strike
      // iflt = 2 -> use finite line faults for M>6 and fix strike
      // iflt = 3 -> use finite faults with Johston mblg to Mw converter
      // iflt = 4 -> use finite faults with Boore and Atkinson mblg to Mw
      // converter
      // ibmax = 0 -> use specified b value
      // ibmax = 1 -> use b value matrix (provided in a file)
      // maxMat = 0 -> use specified maximum magnitude
      // maxMat = 1 -> use maximum magnitude matrix (provided in a file)
      // maxMat = -1 -> use as maximum magnitude the minimum between the
      // default and grid value
      String grdDat = lines.next();
      srcDat.fltCode = FaultCode.typeForID(Parsing.readInt(grdDat, 0));
      srcDat.bGrid = Parsing.readInt(grdDat, 1) > 0 ? true : false;
      srcDat.mMaxGrid = Parsing.readInt(grdDat, 2) > 0 ? true : false;
      srcDat.mTaper = Parsing.readDouble(grdDat, 3); // magnitude at which
      // wtGrid is applied
      srcDat.weightGrid = srcDat.mTaper > 0 ? true : false;

      if (srcDat.bGrid) srcDat.bGridURL = readSourceURL(lines.next(), sf);
      if (srcDat.mMaxGrid) srcDat.mMaxGridURL = readSourceURL(lines.next(), sf);
      if (srcDat.weightGrid) srcDat.weightGridURL = readSourceURL(lines.next(), sf);
      srcDat.aGridURL = readSourceURL(lines.next(), sf);

      // read rate information if rateType is CUMULATIVE
      // it will require conversion to INCREMENTAL
      readRateInfo(lines.next(), srcDat);

      // read strike or rjb array
      if (srcDat.fltCode == FIXED) {
        double strike = Parsing.readDouble(lines.next(), 0);
        if (strike < 0.0) strike += 360.0;
        srcDat.strike = strike;
      }

      srcDat.rupScaling = NSHM_SUB_GEOMAT_LENGTH;

      // done reading; skip atten rel configs

      srcDat.region = Regions.createRectangularGridded(
          "NSHMP " + srcDat.name,
          Location.create(srcDat.minLat, srcDat.minLon),
          Location.create(srcDat.maxLat, srcDat.maxLon),
          srcDat.dLat, srcDat.dLon,
          GriddedRegion.ANCHOR_0_0);

      log.info(srcDat.toString());

      initDataGrids(srcDat);

      String S = File.separator;
      String outPath = dir + S + sf.region + S + sf.type + S;
      String outNameBase = sf.name.substring(0, sf.name.lastIndexOf('.'));

      for (Map.Entry<Double, Range<Double>> entry : srcDat.lonDepthMap.entrySet()) {
        srcDat.depth = entry.getKey();
        srcDat.maxDepth = srcDat.depth + 8;
        String outName = outNameBase + "_" + entry.getKey().intValue() + "km.xml";
        File outFile = new File(outPath, outName);
        Files.createParentDirs(outFile);
        srcDat.writeXML(outFile, entry.getValue());
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "Grid parse error: exiting", e);
      System.exit(1);
    }

  }

  /*
   * NOTE: This line reads longitude and depth information NOT the usual depth
   * distribution data as that found in other fortran grid source input files.
   */
  private static void readRuptureTop(String line, SlabSourceData2014 gsd) {
    List<Double> depthDat = splitToDoubleList(line, SPACE);
    Map<Double, Range<Double>> lonDepths = Maps.newHashMap();
    lonDepths.put(depthDat.get(1), Range.atMost(depthDat.get(3)));
    lonDepths.put(depthDat.get(4), Range.open(depthDat.get(3), depthDat.get(6)));
    lonDepths.put(depthDat.get(7), Range.atLeast(depthDat.get(6)));
    gsd.lonDepthMap = lonDepths;
  }

  private static void readMechWeights(String line, SlabSourceData2014 gsd) {
    List<Double> weights = splitToDoubleList(line, SPACE);
    Map<FocalMech, Double> map = Maps.newEnumMap(FocalMech.class);
    map.put(STRIKE_SLIP, weights.get(0));
    map.put(REVERSE, weights.get(1));
    map.put(NORMAL, weights.get(2));
    gsd.mechWtMap = map;
  }

  private static void readLookupArrayDat(String line, SlabSourceData2014 gsd) {
    List<Double> rDat = splitToDoubleList(line, SPACE);
    gsd.dR = rDat.get(0).intValue();
    gsd.rMax = rDat.get(1).intValue();
  }

  private static void readSourceLatRange(String line, SlabSourceData2014 gsd) {
    List<Double> latDat = splitToDoubleList(line, SPACE);
    gsd.minLat = latDat.get(0);
    gsd.maxLat = latDat.get(1);
    gsd.dLat = latDat.get(2);
  }

  private static void readSourceLonRange(String line, SlabSourceData2014 gsd) {
    List<Double> lonDat = splitToDoubleList(line, SPACE);
    gsd.minLon = lonDat.get(0);
    gsd.maxLon = lonDat.get(1);
    gsd.dLon = lonDat.get(2);
  }

  private static URL readSourceURL(String path, SourceFile sf)
      throws MalformedURLException {
    String srcURL = sf.url.toString();
    String grdURL = srcURL.substring(0, srcURL.lastIndexOf(SRC_DIR)) +
        path.substring(3);
    return new URL(grdURL);
  }

  private static void readRateInfo(String line, SlabSourceData2014 gsd) {
    List<Double> rateDat = splitToDoubleList(line, SPACE);
    gsd.timeSpan = rateDat.get(0);
    gsd.rateType = (rateDat.get(1).intValue() == 0) ? INCREMENTAL : CUMULATIVE;
  }

  private static void createGridSource(SlabSourceData2014 gsd) throws IOException {

    initDataGrids(gsd);

    GriddedRegion region = Regions.createRectangularGridded(
        "NSHMP " + gsd.name,
        Location.create(gsd.minLat, gsd.minLon),
        Location.create(gsd.maxLat, gsd.maxLon),
        gsd.dLat, gsd.dLon,
        GriddedRegion.ANCHOR_0_0);

    // generateMFDs(region);
    // initSrcRegion(region);

    // // KLUDGY: need to post process CEUS grids to handle craton and
    // // extended margin weighting grids
    // if (srcName.contains("2007all8")) {
    // ceusScaleRates();
    // }
    //
    // GridERF gs = new GridERF(srcName, generateInfo(), border,
    // srcLocs, mfdList, depths, mechWtMap, fltCode, strike, srcRegion,
    // srcIMR, srcWt, rMax, dR);
    // return gs;
  }

  private static void initDataGrids(SlabSourceData2014 gsd) throws IOException {
    int nRows = (int) Math.rint((gsd.maxLat - gsd.minLat) / gsd.dLat) + 1;
    int nCols = (int) Math.rint((gsd.maxLon - gsd.minLon) / gsd.dLon) + 1;

    // always have an a-grid file
    gsd.aDat = readGrid(gsd.aGridURL, nRows, nCols, 0);

    // might have a b-grid file, but not likely
    if (gsd.bGrid) {
      gsd.bDat = readGrid(gsd.bGridURL, nRows, nCols, 0);
      // KLUDGY numerous b-values are 0 but there is a hook in
      // hazgridXnga5 (line 931) to override a grid based b=0 to the
      // b-value set in the config for a grid source.
      for (int i = 0; i < gsd.bDat.length; i++) {
        if (gsd.bDat[i] == 0.0) gsd.bDat[i] = gsd.grDat.bVal;
      }
    }

    // variable mMax is common
    if (gsd.mMaxGrid) {
      gsd.mMaxDat = readGrid(gsd.mMaxGridURL, nRows, nCols, 0);
    }

    // weights; mostly for CA
    if (gsd.weightGrid) {
      gsd.wgtDat = readGrid(gsd.weightGridURL, nRows, nCols, 0);
    }
  }

  private static double[] makeGrid(int size, double value) {
    double[] dat = new double[size];
    Arrays.fill(dat, value);
    return dat;
  }

}
