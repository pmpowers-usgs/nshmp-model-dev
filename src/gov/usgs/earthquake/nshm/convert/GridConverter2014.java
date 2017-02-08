package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkNotNull;
import static gov.usgs.earthquake.nshm.util.FaultCode.FIXED;
import static gov.usgs.earthquake.nshm.util.FaultCode.LONG_HEADER;
import static gov.usgs.earthquake.nshm.util.RateType.CUMULATIVE;
import static gov.usgs.earthquake.nshm.util.RateType.INCREMENTAL;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CEUS;
import static gov.usgs.earthquake.nshm.util.Utils.readGrid;
import static org.opensha2.eq.fault.FocalMech.NORMAL;
import static org.opensha2.eq.fault.FocalMech.REVERSE;
import static org.opensha2.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_POINT_WC94_LENGTH;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_SOMERVILLE;
import static org.opensha2.internal.Parsing.splitToDoubleList;
import static org.opensha2.internal.Parsing.Delimiter.SPACE;

import gov.usgs.earthquake.nshm.util.FaultCode;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha2.data.Data;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Region;
import org.opensha2.geo.Regions;
import org.opensha2.internal.MathUtils;
import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Files;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

/*
 * Convert 2014 CEUS NSHMP grid input files to XML.
 * 
 * NOTE: Western US grid files should route through regular converter.
 * 
 * This class was required because 2014 CEUS-wide grid sources have novel Mmax
 * zonations that do not parse. The associated mMax file gives a zone id that is
 * then used to pick an mMax distribution listed in the input file. This parser
 * separates out each source by zone and sets up grid files that define multiple
 * default GR Mfds.
 * 
 * Converseley, all the RLMEs are defined with individual mag files that can be
 * consolidated into single files with multiple SINGLE MFDs because the a-values
 * are the same for all magnitudes.
 * 
 * @author Peter Powers
 */
class GridConverter2014 {

  private Logger log;

  private GridConverter2014() {}

  static GridConverter2014 create(Logger log) {
    GridConverter2014 gc = new GridConverter2014();
    gc.log = checkNotNull(log);
    return gc;
  }

  // path to binary grid files in grid source input files
  private static final String SRC_DIR = "conf";

  void convert(List<SourceFile> files, String dir) {

    Multimap<String, GridSourceData2014> srcDatMap = ArrayListMultimap.create();

    // because we're trying to consolidate RLME mMax branches we agreggate
    // parsed file data so we can extract SINGLE mag and weight data
    for (SourceFile file : files) {
      String id = Iterables.get(Parsing.split(file.name, Delimiter.PERIOD), 0);
      if (id.startsWith("CEUSchar") || id.contains("RLME")) {
        GridSourceData2014 srcDat = convert(file);
        srcDatMap.put(id, srcDat);
      } else {
        GridSourceData2014 srcDat = convert(file);
        exportZoned(srcDat, dir);
      }
    }

    // create map of reference source data that will contain multiple ch mfds
    Map<String, GridSourceData2014> rlmeRefSrcDat = new HashMap<>();
    for (String id : srcDatMap.keySet()) {
      Collection<GridSourceData2014> srcDatList = srcDatMap.get(id);
      for (GridSourceData2014 srcDat : srcDatList) {
        if (rlmeRefSrcDat.containsKey(id)) {
          rlmeRefSrcDat.get(id).chDats.add(srcDat.chDat);
        } else {
          srcDat.chDats = Sets.newTreeSet(Ordering.natural().onResultOf(
              new Function<CH_Data, Double>() {
                @Override
                public Double apply(CH_Data input) {
                  return input.mag;
                }
              }));
          srcDat.chDats.add(srcDat.chDat);
          rlmeRefSrcDat.put(id, srcDat);
        }
      }
    }

    // now export consolidated data
    for (GridSourceData2014 srcDat : rlmeRefSrcDat.values()) {
      exportRlme(srcDat, dir);
    }

  }

  void exportRlme(GridSourceData2014 srcDat, String dir) {

    // correct SINGLE mag weights in RLMEs; want to have mMax distribution
    // weights sum to 1.0, matched to correct file weight (sum of original
    // individual fortran input file weights)

    double fileWeight = Data.sum(FluentIterable.from(srcDat.chDats).transform(
        new Function<CH_Data, Double>() {
          @Override
          public Double apply(CH_Data chData) {
            return chData.weight;
          }
        }).toList());
    // summing (above) induces rounding errors
    fileWeight = MathUtils.round(fileWeight, 8);
    srcDat.weight = fileWeight;
    for (CH_Data chDat : srcDat.chDats) {
      chDat.weight /= fileWeight;
      chDat.weight = MathUtils.round(chDat.weight, 8);
    }

    try {
      String S = File.separator;
      String outPath = dir + S + srcDat.srcRegion + S + srcDat.srcType + S + "rlme" + S;
      String cleanedName = cleanRlmeName(srcDat.name);
      srcDat.fileName = cleanedName + ".xml";
      srcDat.displayName = cleanedName;
      File outFile = new File(outPath, srcDat.fileName);
      Files.createParentDirs(outFile);
      srcDat.writeXML(outFile, -1);

    } catch (Exception e) {
      log.log(Level.SEVERE, "Grid export error: exiting", e);
      System.exit(1);
    }

  }

  void exportZoned(GridSourceData2014 srcDat, String dir) {
    try {
      String S = File.separator;
      String srcName = srcDat.name.substring(0, srcDat.name.lastIndexOf('.'));
      String outPath = dir + S + srcDat.srcRegion + S + srcDat.srcType + S +
          (srcName.contains("2zone") ? "usgs" : "sscn") +
          (srcName.contains("adapt") ? "-adapt" : "-fixed") + S;
      String displayNameBase = (srcName.contains("2zone") ? "USGS " : "SSCn ") +
          (srcName.contains("adapt") ? "Adaptive Smoothing " : "Fixed Smoothing ");

      for (int i = 0; i < srcDat.mMaxWtMaps.size(); i++) {
        // skip empty zones
        double mMaxZoneValue = new Integer(i + 1).doubleValue();
        if (srcDat.mMaxZoneBag.count(mMaxZoneValue) == 0) continue;

        srcDat.fileName = "Zone " + (i + 1) + ".xml";
        srcDat.displayName = displayNameBase + "Zone " + (i + 1);
        File outFile = new File(outPath, srcDat.fileName);
        Files.createParentDirs(outFile);
        srcDat.writeXML(outFile, i);
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "Grid export error: exiting", e);
      System.exit(1);
    }

  }

  GridSourceData2014 convert(SourceFile sf) {

    try {
      log.info("Starting source: " + sf.name);
      GridSourceData2014 srcDat = new GridSourceData2014();
      srcDat.name = sf.name;
      srcDat.id = -1;
      srcDat.weight = sf.weight;
      srcDat.srcRegion = sf.region;
      srcDat.srcType = sf.type;

      Iterator<String> lines = sf.lineIterator();

      // grid of sites (1-30) or station list (0)
      int numSta = Parsing.readInt(lines.next(), 0);
      // skip stations or lat-lon bounds
      Iterators.advance(lines, (numSta > 0) ? numSta : 2);
      // skip site data (Vs30) and Campbell basin depth
      lines.next();
      // read rupture top data (num, [z, wt M<=6.5, wt M>6.5], ...)
      readRuptureTop(lines.next(), srcDat);
      srcDat.depthMag = 6.5;
      srcDat.maxDepth = (sf.region == CEUS) ? 22.0 : 14.0;
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

        // KLUDGY ultimately for the RLMEs we'll just be consolidating
        // chData fields so we need to move the file weight to chDat
        if (!sf.name.contains("zone")) {
          srcDat.chDat.weight = srcDat.weight;
        }
      }

      // iflt, ibmat, maxMat, Mtaper
      // iflt = 0 -> no finite faults
      // iflt = 1 -> apply finite fault corrections for M>6 assuming random
      // strike
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
      int mMaxFlag = Parsing.readInt(grdDat, 2);
      srcDat.mMaxGrid = mMaxFlag > 0 ? true : false;
      srcDat.mTaper = Parsing.readDouble(grdDat, 3); // magnitude at which
                                                     // wtGrid is applied
      srcDat.weightGrid = srcDat.mTaper > 0 ? true : false;

      // if (!srcDat.mMaxGrid) throw new IllegalStateException("must have mMax
      // flag data");

      if (srcDat.bGrid) srcDat.bGridURL = readSourceURL(lines.next(), sf);
      if (srcDat.mMaxGrid) srcDat.mMaxGridURL = readSourceURL(lines.next(), sf);
      if (srcDat.weightGrid) srcDat.weightGridURL = readSourceURL(lines.next(), sf);

      readMaxDistros(lines, mMaxFlag, srcDat);

      srcDat.aGridURL = readSourceURL(lines.next(), sf);

      // read rate information if rateType is CUMULATIVE
      // it will require conversion to INCREMENTAL
      readRateInfo(lines.next(), srcDat);

      // read strike or rjb array
      if (srcDat.fltCode == FIXED || srcDat.fltCode == LONG_HEADER) {
        double strike = Parsing.readDouble(lines.next(), 0);
        if (strike < 0.0) strike += 360.0;
        srcDat.strike = strike;
      }

      srcDat.rupScaling = srcDat.name.contains("zone") ? NSHM_SOMERVILLE
          : NSHM_POINT_WC94_LENGTH;

      // done reading; skip atten rel configs

      srcDat.region = Regions.createRectangularGridded(
          "NSHMP " + srcDat.name,
          Location.create(srcDat.minLat, srcDat.minLon),
          Location.create(srcDat.maxLat, srcDat.maxLon),
          srcDat.dLat, srcDat.dLon,
          GriddedRegion.ANCHOR_0_0);

      initDataGrids(srcDat);

      // now that grids are populated; consolidate zone counts in a bag
      if (srcDat.mMaxGrid) {
        List<Double> mMaxValues = Doubles.asList(srcDat.mMaxDat);
        srcDat.mMaxZoneBag = TreeMultiset.create(mMaxValues);
      }

      log.info(srcDat.toString());

      return srcDat;

    } catch (Exception e) {
      log.log(Level.SEVERE, "Grid parse error: exiting", e);
      System.exit(1);
    }

    return null;
  }

  /*
   * This line is set up to configure a probability distribution of magnitude
   * dependent rupture top depths. These are actually not used in favor of fixed
   * values for M<6.5 and M>=6.5
   */
  private static void readRuptureTop(String line, GridSourceData2014 gsd) {
    List<Double> depthDat = splitToDoubleList(line, SPACE);
    int numDepths = depthDat.get(0).intValue();
    double loMagDepth, hiMagDepth;
    if (numDepths == 1) {
      loMagDepth = depthDat.get(1);
      hiMagDepth = depthDat.get(1);
    } else {
      loMagDepth = depthDat.get(4);
      hiMagDepth = depthDat.get(1);
    }
    gsd.depths = new double[] { loMagDepth, hiMagDepth };
  }

  private static void readMechWeights(String line, GridSourceData2014 gsd) {
    List<Double> weights = splitToDoubleList(line, SPACE);
    Map<FocalMech, Double> map = Maps.newEnumMap(FocalMech.class);
    map.put(STRIKE_SLIP, weights.get(0));
    map.put(REVERSE, weights.get(1));
    map.put(NORMAL, weights.get(2));
    gsd.mechWtMap = map;
  }

  private static void readLookupArrayDat(String line, GridSourceData2014 gsd) {
    List<Double> rDat = splitToDoubleList(line, SPACE);
    gsd.dR = rDat.get(0).intValue();
    gsd.rMax = rDat.get(1).intValue();
  }

  private static void readSourceLatRange(String line, GridSourceData2014 gsd) {
    List<Double> latDat = splitToDoubleList(line, SPACE);
    gsd.minLat = latDat.get(0);
    gsd.maxLat = latDat.get(1);
    gsd.dLat = latDat.get(2);
  }

  private static void readSourceLonRange(String line, GridSourceData2014 gsd) {
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

  private static void readMaxDistros(Iterator<String> lines, int size, GridSourceData2014 gsd) {
    List<Map<Double, Double>> mMaxWtMaps = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      Map<Double, Double> mMaxWtMap = new TreeMap<>();
      String line = lines.next();
      List<Double> values = Parsing.splitToDoubleList(line, Delimiter.SPACE);
      for (int j = 2; j < values.size(); j += 2) {
        mMaxWtMap.put(values.get(j), values.get(j + 1));
      }
      mMaxWtMaps.add(mMaxWtMap);
    }
    gsd.mMaxWtMaps = mMaxWtMaps;
  }

  private static void readRateInfo(String line, GridSourceData2014 gsd) {
    List<Double> rateDat = splitToDoubleList(line, SPACE);
    gsd.timeSpan = rateDat.get(0);
    gsd.rateType = (rateDat.get(1).intValue() == 0) ? INCREMENTAL : CUMULATIVE;
  }

  // long headers were added to Charleston aGrids in 2014
  private static final int LONG_HEAD_SIZE = 896; // in bytes

  private static void initDataGrids(GridSourceData2014 gsd) throws IOException {
    int nRows = (int) Math.rint((gsd.maxLat - gsd.minLat) / gsd.dLat) + 1;
    int nCols = (int) Math.rint((gsd.maxLon - gsd.minLon) / gsd.dLon) + 1;

    // always have an a-grid file
    gsd.aDat = (gsd.fltCode == LONG_HEADER) ? readGrid(gsd.aGridURL, nRows, nCols, LONG_HEAD_SIZE)
        : readGrid(gsd.aGridURL, nRows, nCols, 0);

    // System.out.println(gsd.aGridURL);
    // double[] raw = readGrid(gsd.aGridURL);
    // System.out.println(Arrays.toString(raw));

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

  private static String cleanRlmeName(String name) {
    if (name.contains("char_2014_l")) return "Charleston Local Zone";
    if (name.contains("char_2014_n")) return "Charleston Narrow Zone";
    if (name.contains("char_2014_r")) return "Charleston Regional Zone";
    if (name.contains("Chlvx")) return "Charlevoix Seismic Zone";
    if (name.contains("Commerce")) return "Commerce Lineament";
    if (name.contains("ERM-S1")) return "Eastern Rift Margin S1";
    if (name.contains("ERM-S2")) return "Eastern Rift Margin S2";
    if (name.contains("ERM-N")) return "Eastern Rift Margin N";
    if (name.contains("Wabash")) return "Wabash Valley";
    if (name.contains("Marianna")) return "Marianna Source Zone";
    throw new IllegalStateException("Unrecognized name:" + name);
  }

}
