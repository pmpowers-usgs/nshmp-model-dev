package gov.usgs.earthquake.nshm.nz;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opensha2.eq.Earthquakes;
import org.opensha2.eq.TectonicSetting;
import org.opensha2.eq.fault.Faults;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.mfd.IncrementalMfd;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Maths;

import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.common.primitives.Doubles;

/*
 * Aggregates fault and gris source data from NZ input files.
 * 
 * @author Peter Powers
 */
class NewZealandParser {

  private static final String PATH = "/Users/pmpowers/Documents/NSHMP/Forecasts/nz/code";
  private static final Splitter SPLIT = Splitter.on(" ").omitEmptyStrings();
  private static final Path gridPath = Paths.get(PATH, "backgroundGrid.txt");
  private static final Path faultPath = Paths.get(PATH, "FUN1111.DAT");

  private static final double M4_FLOOR = 0.0008;

  NewZealandParser() {
    initFaults();
    initGrid();
  }

  // fault data aggregators
  private List<String> names;
  private List<Double> rakes;
  private List<Double> mags;
  private List<Double> recurs;
  private List<Double> dips;
  private List<Double> zTops;
  private List<Double> zBots;
  private List<LocationList> traces;

  private ListMultimap<TectonicSetting, Integer> typeMap;

  private void initFaults() {

    names = Lists.newArrayList();
    rakes = Lists.newArrayList();
    mags = Lists.newArrayList();
    recurs = Lists.newArrayList();
    dips = Lists.newArrayList();
    zTops = Lists.newArrayList();
    zBots = Lists.newArrayList();
    traces = Lists.newArrayList();

    ImmutableListMultimap.Builder<TectonicSetting, Integer> typeMapBuilder =
        ImmutableListMultimap.builder();

    // @formatter:off
		//
		// Example input:
		//
		// AhuririR rv											fault-name
		// 3D													section-count
		// 45.0 280.0 12.0 0.0									dip dip-dir depth-base depth-top
		// 44 4.2 169 42.0 44 26.7 169 41.5    7.2    6100		endpoints: lat(deg min) lon(deg min) x2 mag recurrence
		// 44 4.2 169 42.0 44 12.8 169 37.4						sections: lat(deg min) lon(deg min)
		// 44 12.8 169 37.4 44 17.9 169 36.8					...
		// 44 17.9 169 36.8 44 26.7 169 41.5
		// -1													end	
		//
		// @formatter:on

    // URL url = Resources.getResource(NewZealandParser.class,
    // faultPath.toString());
    List<String> lines = null;
    try {
      lines = Resources.readLines(faultPath.toUri().toURL(), StandardCharsets.US_ASCII);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    Iterator<String> lineIterator = Iterables.skip(lines, 3).iterator(); // skip
                                                                         // a
                                                                         // and
                                                                         // b
                                                                         // data

    int index = 0;
    while (lineIterator.hasNext()) {

      // get name and slip style
      List<String> nameSlip = SPLIT.splitToList(lineIterator.next());
      String name = nameSlip.get(0);
      names.add(name);
      NZ_SourceID id = NZ_SourceID.fromString(nameSlip.get(1));
      TectonicSetting tect = id.tectonicType();
      typeMapBuilder.put(tect, index++);
      rakes.add(id.rake());

      // get section count
      String sizeID = lineIterator.next().trim();
      int dIndex = sizeID.indexOf("D");
      String sizeStr = sizeID.substring(0, dIndex);
      int size = Integer.parseInt(sizeStr);

      // get geometry data
      List<Double> geomValues = lineToDoubleList(lineIterator.next());
      dips.add(geomValues.get(0));
      double dipDir = geomValues.get(1);
      zTops.add(geomValues.get(3));
      zBots.add(geomValues.get(2));

      // trace endpoint specification -- mostly ignored
      List<Double> traceData = lineToDoubleList(lineIterator.next());
      mags.add(traceData.get(8));
      recurs.add(traceData.get(9));

      // build trace
      LocationList.Builder traceBuilder = LocationList.builder();
      for (int i = 0; i < size; i++) {
        String locLine = lineIterator.next();
        traceBuilder.add(parseLocation(locLine));
        if (i == size - 1) {
          traceBuilder.add(parseLocation2(locLine));
        }
      }
      LocationList trace = traceBuilder.build();

      // to adhere to the right hand rule, we need to check that the
      // reported dip direction is correct (within 90 degrees of the
      // dip direction derived from the trace as reported. If not,
      // the trace is reversed. We do not preserve dip direction data
      // after parsing.
      traces.add(validateTrace(trace, dipDir));

      // skip closing -1
      lineIterator.next();
    }
    typeMap = typeMapBuilder.build();
    checkState(names.size() == typeMap.values().size());
  }

  class FaultData {
    final String name;
    final double mag;
    final double rate;
    final double dip;
    final double zTop;
    final double width;
    final double rake;
    final LocationList trace;

    private FaultData(String name, double mag, double rate, double dip, double zTop,
        double width, double rake, LocationList trace) {
      this.name = name;
      this.mag = mag;
      this.rate = rate;
      this.dip = dip;
      this.zTop = zTop;
      this.width = width;
      this.rake = rake;
      this.trace = trace;
    }
  }

  Set<TectonicSetting> tectonicSettings() {
    return typeMap.keySet();
  }

  List<FaultData> getSourceDataList(TectonicSetting tect) {
    List<FaultData> sourceDataList = new ArrayList<>();
    for (Integer i : typeMap.get(tect)) {
      double rate = 1.0 / recurs.get(i);
      double width = (zBots.get(i) - zTops.get(i)) / Math.sin(dips.get(i) * Maths.TO_RAD);
      FaultData faultData = new FaultData(names.get(i), mags.get(i), rate,
          dips.get(i), zTops.get(i), width, rakes.get(i), traces.get(i));
      sourceDataList.add(faultData);
    }
    return sourceDataList;
  }

  // All incoming lats need to be converted to southern hemi values.
  private static Location parseLocation(String line) {
    List<Double> vals = lineToDoubleList(line);
    double lat = vals.get(0) + vals.get(1) / 60.0;
    double lon = vals.get(2) + vals.get(3) / 60.0;
    return Location.create(-lat, lon);
  }

  // reads the second location; only used at last section
  private static Location parseLocation2(String line) {
    List<Double> vals = lineToDoubleList(line);
    double lat = vals.get(4) + vals.get(5) / 60.0;
    double lon = vals.get(6) + vals.get(7) / 60.0;
    return Location.create(-lat, lon);
  }

  // convert line of space delimited numbers to list of double values
  private static List<Double> lineToDoubleList(String line) {
    return FluentIterable.from(SPLIT.splitToList(line)).transform(Doubles.stringConverter())
        .toList();
  }

  static final double M_MIN = 5.05;
  static final double D_MAG = 0.1;
  static final double D_MAG_BY_2 = 0.05;

  // grid data aggregators
  List<Location> locs;
  List<NZ_SourceID> ids;
  // List<IncrementalMfd> mfds;

  List<Double> aVals;
  List<Double> bVals;
  List<Double> mMaxs;

  private void initGrid() {

    locs = Lists.newArrayList();
    ids = Lists.newArrayList();
    aVals = Lists.newArrayList();
    bVals = Lists.newArrayList();
    mMaxs = Lists.newArrayList();

    // @formatter:off
		//
		// 1          2          3        4    5   6    7  8    9    10     11      12
		// 0.000000   0.000000   0.000000 1.12 7.2 1.00 nn 0.00 0.00 34.200 173.000 10.0
		//
		// 1 = # events >= M4   for period 1964 to 2009 inclusive
		// 2 = # events >= M5   for period 1940 to 1963 inclusive
		// 3 = # events >= M6.5 for period 1840 to 1939 inclusive
		// 4 = b-value
		// 5 = mMax
		// 6 = (not used)
		// 7 = slip type (may be absent; use 'sr')
		// 8 = (not used)
		// 9 = (not used)
		// 10 = lat (needs to be converted to negative)
		// 11 = lon
		// 12 = depth (file includes depths: 10, 30, 50, 70, and 90 km)
		//
		// @formatter:on

    List<String> lines = null;
    try {
      lines = Resources.readLines(gridPath.toUri().toURL(), StandardCharsets.US_ASCII);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }

    for (String line : lines) {
      List<String> values = SPLIT.splitToList(line);
      double m4rate = Double.parseDouble(values.get(0));
      double m5rate = Double.parseDouble(values.get(1));
      double m6p5rate = Double.parseDouble(values.get(2));
      double bVal = Double.parseDouble(values.get(3));
      double mMax = Double.parseDouble(values.get(4)) - D_MAG_BY_2;

      // compute a-value
      double aVal = aValueCalc(m4rate, m5rate, m6p5rate, bVal); // a @ M=0

      // if rate at M=4 < 0.0008, scale
      // double m4test = MagUtils.gr_rate(aVal, bVal, 4.0);
      double m4test = Mfds.grRate(aVal, bVal, 4.0);
      if (m4test < M4_FLOOR) {
        double aScale = Math.log10(M4_FLOOR) - Math.log10(m4test);
        aVal += aScale;
      }

      // build mfd
      // double aValMinM = MagUtils.gr_rate(aVal, bVal, M_MIN);
      double aValMinM = Mfds.grRate(aVal, bVal, M_MIN);
      // int nMag = (int) ((mMax - M_MIN) / D_MAG + 1.4);
      // GutenbergRichterMagFreqDist mfd = new
      // GutenbergRichterMagFreqDist(M_MIN, nMag, D_MAG);
      // mfd.setAllButTotMoRate(M_MIN, mMax, aValMinM, bVal);
      // GutenbergRichterMfd mfd = Mfds.newGutenbergRichterMFD(M_MIN, D_MAG,
      // nMag, bVal, aValMinM);
      // mfds.add(mfd);

      aVals.add(aValMinM);
      bVals.add(bVal);
      mMaxs.add(mMax);

      // get source type id; ise 'sr' for empty value and offset indexing
      // KLUDGY -- probably should just use substrings to extract values
      NZ_SourceID id = NZ_SourceID.SR;
      int offset = 0;
      try {
        id = NZ_SourceID.fromString(values.get(6));
      } catch (IllegalArgumentException iae) {
        offset = -1;
      }
      // TODO revisit this after consulting with Mark
      // change RO's to SR's
      if (id == NZ_SourceID.RO) id = NZ_SourceID.SR;

      ids.add(id);

      // get location and depth
      double lat = -Double.parseDouble(values.get(9 + offset));
      double lon = Double.parseDouble(values.get(10 + offset));
      double depth = Double.parseDouble(values.get(11 + offset));
      Location loc = Location.create(lat, lon, depth);
      locs.add(loc);

    }
  }

  // List<ProbEqkSource> getGridSources(double duration) {
  // List<ProbEqkSource> sources = Lists.newArrayList();
  // for (int i=0; i<locs.size(); i++) {
  // Location loc = locs.get(i);
  // PointSource13b ptSrc = new PointSource13b(
  // loc,
  // mfds.get(i),
  // duration,
  // new double[] {loc.getDepth(), loc.getDepth()},
  // ids.get(i).mechWtMap());
  // sources.add(ptSrc);
  // }
  // return sources;
  // }

  // catalog time before's
  private static final double CT_M4 = 46; // 2009-1964 (inclusive) so +1
  private static final double CT_M5 = 25; // 1963-1940 (inclusive) so +1
  private static final double CT_M6P5 = 101; // 1939-1840 (inclusive) so +1

  /*
   * Maximum likelihood a-value calculation for multiple catalogs with variable
   * mMin
   */
  private static double aValueCalc(double rate_m4, double rate_m5, double rate_m6p5, double b) {
    // a value formulation
    double tb1 = CT_M4 * Math.pow(10, 4.0 * -b);
    double tb2 = CT_M5 * Math.pow(10, 5.0 * -b);
    double tb3 = CT_M6P5 * Math.pow(10, 6.5 * -b);
    double rateSum = rate_m4 + rate_m5 + rate_m6p5;
    // if rateSum = 0.0, then the method will return -Infinity, so we return
    // a very small a-value instead (-30)
    return rateSum <= 0.0 ? -30 : Math.log10(rateSum / (tb1 + tb2 + tb3));
  }

  /*
   * Reverses trace if it does not adhere to right hand rule.
   */
  private static LocationList validateTrace(LocationList trace, double dipDir) {
    double traceDipDir = Faults.dipDirectionRad(trace);
    double inputDipDir = dipDir * Maths.TO_RAD;
    // dot-product derived angle between two dip direction unit vectors
    double angle = Math.acos(Math.sin(traceDipDir) * Math.sin(inputDipDir) +
        Math.cos(traceDipDir) * Math.cos(inputDipDir)) *
        Maths.TO_DEG;
    return (angle > 90.0) ? trace.reverse() : trace;
  }

  public static void main(String[] args) throws IOException {
    // loadFaultSources();
    NewZealandParser parser = new NewZealandParser();
    // List<ProbEqkSource> sources = parser.getGridSources(1.0);
    // for (ProbEqkSource src : sources) {
    // System.out.println(src);
    // }
    Set<NZ_SourceID> idSet = EnumSet.copyOf(parser.ids);
    System.out.println(idSet);
  }
}
