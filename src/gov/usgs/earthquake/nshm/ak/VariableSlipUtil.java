package gov.usgs.earthquake.nshm.ak;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshm.ak.AkData.*;

import gov.usgs.earthquake.data.Data;
import gov.usgs.earthquake.data.XyPoint;
import gov.usgs.earthquake.data.XySequence;
import gov.usgs.earthquake.eq.Earthquakes;
import gov.usgs.earthquake.eq.fault.Faults;
import gov.usgs.earthquake.geo.LocationList;
import gov.usgs.earthquake.internal.Parsing;
import gov.usgs.earthquake.mfd.IncrementalMfd;
import gov.usgs.earthquake.mfd.Mfds;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Doubles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Convert variable slip along strike of single fault to fault system source.
 *
 * @author Peter Powers
 */
public class VariableSlipUtil {

  /*
   * Methodology:
   * 
   * Assumptions: all sections have the same trace length and down-dip width.
   * 
   * Split fault traces into ~4km long sections.
   * 
   * Interpolate known slip rates across sections.
   * 
   * Using original MFDs, determine the number of adjacent sections (rupture
   * length) for each magnitude.
   * 
   * For each magnitude, compute the number of ruptures a fault can accomodate
   * and create rupture lists.
   * 
   * For each magnitude, compute average slip rates from participating sections
   * for all ruptures and normalize.
   * 
   * Compute total moment rates for: Denali (Center + East), Denali (Center +
   * Totschunda), and Castle Mountain from the areas and slip rates of all
   * sections.
   * 
   * Create MFDs from original fortran inputs and scale to total moment rate.
   * 
   * Scale normalized rupture rates by relevant magnitude event rate.
   * 
   * ========
   * 
   * For the Denali - Totschunda system, run the two pairs separately and then
   * combine common ruptures.
   * 
   * Combine GR and CH.
   */

  static final double TARGET_SECTION_LENGTH = 4.0;

  static final String[] grEpiStrings = { "mMax-epi", "mMax", "mMax+epi" };
  static final String[] chEpiStrings = { "m-epi", "m", "m+epi" };

  // @formatter:off
  static final List<LocationList> DENALI_CENTER_TRACES = DENALI_CENTER_TRACE.partition(TARGET_SECTION_LENGTH);
  static final List<LocationList> DENALI_EAST_TRACES = DENALI_EAST_TRACE.partition(TARGET_SECTION_LENGTH);
  static final List<LocationList> TOTSCHUNDA_TRACES = TOTSCHUNDA_TRACE.partition(TARGET_SECTION_LENGTH);
  static final List<LocationList> CASTLE_MTN_TRACES = CASTLE_MTN_TRACE.partition(TARGET_SECTION_LENGTH);
  
  static final List<Double> DENALI_CENTER_EAST_SLIP_RATES = computeSectionSlip(DENALI_CENTER_TRACE, DENALI_CENTER_TRACES, DENALI_CENTER_EAST_SLIP);
  static final List<Double> DENALI_CENTER_TOTSCHUNDA_SLIP_RATES = computeSectionSlip(DENALI_CENTER_TRACE, DENALI_CENTER_TRACES, DENALI_CENTER_TOTSCHUNDA_SLIP);
  static final List<Double> DENALI_EAST_SLIP_RATES = computeSectionSlip(DENALI_EAST_TRACE, DENALI_EAST_TRACES, DENALI_EAST_SLIP);
  static final List<Double> TOTSCHUNDA_SLIP_RATES = computeSectionSlip(TOTSCHUNDA_TRACE, TOTSCHUNDA_TRACES, TOTSCHUNDA_SLIP);
  static final List<Double> CASTLE_MTN_SLIP_RATES = computeSectionSlip(CASTLE_MTN_TRACE, CASTLE_MTN_TRACES, CASTLE_MTN_SLIP);
      
  static final List<FaultSection> DENALI_CENTER_EAST_SECTIONS = denaliCenterEastSections();
  static final List<FaultSection> DENALI_CENTER_TOTSCHUNDA_SECTIONS = denaliCenterTotschundaSections();
  static final List<FaultSection> DENALI_TOTSCHUNDA_SECTIONS = denaliTotschundaSections();
  static final List<FaultSection> CASTLE_MTN_SECTIONS = castleMountainSections();
  
  static final double DENALI_CENTER_EAST_MO_RATE = momentRate(DENALI_CENTER_EAST_SECTIONS);
  static final double DENALI_CENTER_TOTSCHUNDA_MO_RATE = momentRate(DENALI_CENTER_TOTSCHUNDA_SECTIONS);
  static final double CASTLE_MTN_MO_RATE = momentRate(CASTLE_MTN_SECTIONS);

  static final Map<String, IncrementalMfd> DENALI_CENTER_EAST_GR_MFDS = denaliCenterEastGrMfds();
  static final Map<String, IncrementalMfd> DENALI_CENTER_TOTSCHUNDA_GR_MFDS = denaliCenterTotschundaGrMfds();
  static final Map<String, IncrementalMfd> CASTLE_MTN_GR_MFDS = castleMountainGrMfds();
  
  static final Map<String, IncrementalMfd> DENALI_CENTER_EAST_CH_MFDS = denaliCenterEastChMfds();
  static final Map<String, IncrementalMfd> DENALI_CENTER_TOTSCHUNDA_CH_MFDS = denaliCenterTotschundaChMfds();
  static final Map<String, IncrementalMfd> CASTLE_MTN_CH_MFDS = castleMountainChMfds();
  
  static final Map<String, List<Rupture>> DENALI_CENTER_EAST_RUPTURES = denaliCenterEastRuptures();
  static final Map<String, List<Rupture>> DENALI_CENTER_TOTSCHUNDA_RUPTURES = denaliCenterTotschundaRuptures();
  static final Map<String, List<Rupture>> DENALI_TOTSCHUNDA_RUPTURES = denaliTotschundaRuptures();
  static final Map<String, List<Rupture>> CASTLE_MTN_RUPTURES = castleMountainRuptures();
  // @formatter:on

  @SuppressWarnings("javadoc")
  public static void main(String[] args) {
    // reviewSections();
    reviewMfds();
  }

  static void reviewSections() {
    // @formatter:off
    sectionReview("Denali Center + East", DENALI_CENTER_TRACE, DENALI_CENTER_TRACES, DENALI_CENTER_EAST_SLIP_RATES);
    sectionReview("Denali Center + Totschunda", DENALI_CENTER_TRACE, DENALI_CENTER_TRACES, DENALI_CENTER_TOTSCHUNDA_SLIP_RATES);
    sectionReview("Denali East", DENALI_EAST_TRACE, DENALI_EAST_TRACES, DENALI_EAST_SLIP_RATES);
    sectionReview("Totschunda", TOTSCHUNDA_TRACE, TOTSCHUNDA_TRACES, TOTSCHUNDA_SLIP_RATES);
    sectionReview("Castle Mountain", CASTLE_MTN_TRACE, CASTLE_MTN_TRACES, CASTLE_MTN_SLIP_RATES);
    // @formatter:on
  }

  static void reviewMfds() {
    // @formatter:off
    mfdReview("Denali-Denali GR", DENALI_CENTER_EAST_MO_RATE, DENALI_CENTER_EAST_GR_MFDS);
    mfdReview("Denali-Denali CH", DENALI_CENTER_EAST_MO_RATE, DENALI_CENTER_EAST_CH_MFDS);
    mfdReview("Denali-Totschunda GR", DENALI_CENTER_TOTSCHUNDA_MO_RATE, DENALI_CENTER_TOTSCHUNDA_GR_MFDS);
    mfdReview("Denali-Totschunda CH", DENALI_CENTER_TOTSCHUNDA_MO_RATE, DENALI_CENTER_TOTSCHUNDA_CH_MFDS);
    mfdReview("Castle Mountain GR", CASTLE_MTN_MO_RATE, CASTLE_MTN_GR_MFDS);
    mfdReview("Castle Mountain CH", CASTLE_MTN_MO_RATE, CASTLE_MTN_CH_MFDS);
    // @formatter:on
  }

  static void mfdReview(String name, double moRate, Map<String, IncrementalMfd> mfds) {
    System.out.println(name);
    System.out.println();
    System.out.println("MoRate: " + moRate);
    for (Entry<String, IncrementalMfd> mfd : mfds.entrySet()) {
      System.out.println(mfd.getKey());
      System.out.println(mfd.getValue());
      sectionSizeReview(Mfds.toSequence(mfd.getValue()));
    }
  }

  static void sectionSizeReview(XySequence mfd) {
    for (XyPoint xy : mfd) {
      double m = xy.x();
      double length = length(m);
      int sections = magSectionCount(m, TARGET_SECTION_LENGTH);
      double adjLength = sections * TARGET_SECTION_LENGTH;
      System.out.println(String.format(
          "m: %.3f  l1: %7.3f  n: %2d  l2: %5.1f",
          m, length, sections, adjLength));

    }
    System.out.println();
  }

  static int magSectionCount(double m, double sectionLength) {
    double ruptureLength = length(m);
    return (int) Math.rint(ruptureLength / sectionLength);
  }

  static double length(double m) {
    return AK_SCALING.dimensions(m, DENALI_WIDTH).length;
  }

  static List<FaultSection> createSections(
      int startIndex,
      String name,
      List<LocationList> traces,
      List<Double> slipRates,
      double depth,
      double lowerDepth,
      double dip,
      double width,
      double dipDir,
      double aseis) {

    checkArgument(traces.size() == slipRates.size());
    ImmutableList.Builder<FaultSection> sections = ImmutableList.builder();
    for (int i = 0; i < traces.size(); i++) {
      int offsetIndex = startIndex + i;
      FaultSection section = new FaultSection();
      section.index = offsetIndex;
      section.name = name + " [" + offsetIndex + "]";
      section.trace = traces.get(i);
      section.depth = depth;
      section.lowerDepth = lowerDepth;
      section.dip = dip;
      section.width = width;
      section.dipDir = dipDir;
      section.slipRate = slipRates.get(i);
      section.aseis = aseis;
      sections.add(section);
    }
    return sections.build();
  }

  private static List<FaultSection> denaliCenterEastSections() {

    List<FaultSection> sectionsDc = createSections(
        0,
        "Denali Center",
        DENALI_CENTER_TRACES,
        DENALI_CENTER_EAST_SLIP_RATES,
        AK_DEPTH,
        DENALI_CENTER_LOWER_DEPTH,
        DENALI_CENTER_DIP,
        DENALI_CENTER_WIDTH,
        Faults.dipDirection(DENALI_CENTER_TRACE),
        AK_ASEIS);

    List<FaultSection> sectionsDe = createSections(
        sectionsDc.size(),
        "Denali East",
        DENALI_EAST_TRACES,
        DENALI_EAST_SLIP_RATES,
        AK_DEPTH,
        DENALI_EAST_LOWER_DEPTH,
        DENALI_EAST_DIP,
        DENALI_EAST_WIDTH,
        Faults.dipDirection(DENALI_EAST_TRACE),
        AK_ASEIS);

    return ImmutableList.<FaultSection> builder()
        .addAll(sectionsDc)
        .addAll(sectionsDe)
        .build();
  }

  private static List<FaultSection> denaliCenterTotschundaSections() {

    List<FaultSection> sectionsDc = createSections(
        0,
        "Denali Center",
        DENALI_CENTER_TRACES,
        DENALI_CENTER_TOTSCHUNDA_SLIP_RATES,
        AK_DEPTH,
        DENALI_CENTER_LOWER_DEPTH,
        DENALI_CENTER_DIP,
        DENALI_CENTER_WIDTH,
        Faults.dipDirection(DENALI_CENTER_TRACE),
        AK_ASEIS);

    List<FaultSection> sectionsDt = createSections(
        sectionsDc.size() + DENALI_EAST_TRACES.size(),
        "Totschunda",
        TOTSCHUNDA_TRACES,
        TOTSCHUNDA_SLIP_RATES,
        AK_DEPTH,
        TOTSCHUNDA_LOWER_DEPTH,
        TOTSCHUNDA_DIP,
        TOTSCHUNDA_WIDTH,
        Faults.dipDirection(TOTSCHUNDA_TRACE),
        AK_ASEIS);

    return ImmutableList.<FaultSection> builder()
        .addAll(sectionsDc)
        .addAll(sectionsDt)
        .build();
  }

  private static List<FaultSection> denaliTotschundaSections() {

    List<FaultSection> sectionsDc = createSections(
        0,
        "Denali Center",
        DENALI_CENTER_TRACES,
        DENALI_CENTER_EAST_SLIP_RATES,
        AK_DEPTH,
        DENALI_CENTER_LOWER_DEPTH,
        DENALI_CENTER_DIP,
        DENALI_CENTER_WIDTH,
        Faults.dipDirection(DENALI_CENTER_TRACE),
        AK_ASEIS);

    List<FaultSection> sectionsDe = createSections(
        sectionsDc.size(),
        "Denali East",
        DENALI_EAST_TRACES,
        DENALI_EAST_SLIP_RATES,
        AK_DEPTH,
        DENALI_EAST_LOWER_DEPTH,
        DENALI_EAST_DIP,
        DENALI_EAST_WIDTH,
        Faults.dipDirection(DENALI_EAST_TRACE),
        AK_ASEIS);

    List<FaultSection> sectionsDt = createSections(
        sectionsDc.size() + sectionsDe.size(),
        "Totschunda",
        TOTSCHUNDA_TRACES,
        TOTSCHUNDA_SLIP_RATES,
        AK_DEPTH,
        TOTSCHUNDA_LOWER_DEPTH,
        TOTSCHUNDA_DIP,
        TOTSCHUNDA_WIDTH,
        Faults.dipDirection(TOTSCHUNDA_TRACE),
        AK_ASEIS);

    return ImmutableList.<FaultSection> builder()
        .addAll(sectionsDc)
        .addAll(sectionsDe)
        .addAll(sectionsDt)
        .build();
  }

  private static List<FaultSection> castleMountainSections() {

    return createSections(
        0,
        "Castle Mountain",
        CASTLE_MTN_TRACES,
        CASTLE_MTN_SLIP_RATES,
        AK_DEPTH,
        CASTLE_MTN_LOWER_DEPTH,
        CASTLE_MTN_DIP,
        CASTLE_MTN_WIDTH,
        Faults.dipDirection(CASTLE_MTN_TRACE),
        AK_ASEIS);
  }

  /* section data for verification */
  static void sectionReview(
      String name,
      LocationList trace,
      List<LocationList> sections,
      List<Double> sectionSlipRates) {

    System.out.println(name);
    sectionStats(trace, sections);

    for (int i = 0; i < sectionSlipRates.size(); i++) {
      System.out.println(String.format(
          "%3d  %.5f  %s",
          i,
          sections.get(i).length(),
          sectionSlipRates.get(i)));
    }

    System.out.println();
    //
    // for (int i = 0; i < sectionSlipRates.size(); i++) {
    // System.out.println(String.format(
    // "%3d %.5f %s",
    // i,
    // sections.get(i).length(),
    // sectionSlipRates.get(i)));
    // System.out.println(sections.get(i));
    // }
  }

  static void sectionStats(LocationList trace, List<LocationList> sections) {
    double segmentedLen = 0.0;
    for (LocationList list : sections) {
      double len = list.length();
      segmentedLen += len;
      // System.out.println(list);
    }
    System.out.println("    trace length: " + trace.length());
    System.out.println("sectioned length: " + segmentedLen);
    System.out.println("   section count: " + sections.size());
  }

  static Map<String, List<Rupture>> createRuptureLists(
      Map<String, IncrementalMfd> mfds,
      List<FaultSection> sections,
      double dip,
      double depth,
      double width,
      double rake) {

    ImmutableMap.Builder<String, List<Rupture>> ruptureMap = ImmutableMap.builder();
    for (Entry<String, IncrementalMfd> entry : mfds.entrySet()) {
      XySequence mfd = Mfds.toSequence(entry.getValue());
      ImmutableList.Builder<Rupture> ruptureList = ImmutableList.builder();
      for (XyPoint xy : mfd) {
        double m = xy.x();
        int nSections = magSectionCount(m, TARGET_SECTION_LENGTH);
        List<Rupture> ruptures = rupturesForMag(
            sections,
            nSections,
            m,
            xy.y(),
            dip,
            depth,
            width,
            rake);
        ruptureList.addAll(ruptures);
      }
      ruptureMap.put(entry.getKey(), ruptureList.build());
    }
    return ruptureMap.build();
  }

  /*
   * Create all possible ruptures consisting of n sections; called once per
   * magnitude in an MFD.
   */
  static List<Rupture> rupturesForMag(
      List<FaultSection> sections,
      int n,
      double m,
      double mRate,
      double dip,
      double depth,
      double width,
      double rake) {

    /*
     * Build rupture list; temporarily store slip rates separately for scaling.
     */
    ImmutableList.Builder<Rupture> ruptureBuilder = ImmutableList.builder();
    List<Double> slipRates = new ArrayList<>();
    int nRup = ruptureCount(sections.size(), n);
    for (int i = 0; i < nRup; i++) {
      Rupture rupture = new Rupture();
      List<FaultSection> ruptureSections = sections.subList(i, i + n);
      slipRates.add(avgSlipRate(ruptureSections));
      rupture.indices = Parsing.intListToRangeString(Rupture.toIndices(ruptureSections));
      rupture.mag = m;
      rupture.depth = depth;
      rupture.dip = dip;
      rupture.width = width;
      rupture.rake = rake;
      ruptureBuilder.add(rupture);
    }
    List<Rupture> ruptures = ruptureBuilder.build();

    /*
     * Renormalize slip rates across all ruptures, scale to total magnitude
     * rate, and reassign to ruptures.
     */
    slipRates = Data.multiply(mRate, Data.normalize(slipRates));
    for (int i = 0; i < ruptures.size(); i++) {
      ruptures.get(i).rate = slipRates.get(i);
    }

    return ruptures;
  }

  /*
   * The average slip rate over a list of fault sections assuming all sections
   * have equivalent areas.
   */
  static double avgSlipRate(List<FaultSection> sections) {
    double slipSum = 0.0;
    for (FaultSection section : sections) {
      slipSum += section.slipRate;
    }
    return slipSum / sections.size();
  }

  /*
   * The number of unique ruptures that will fit into a larger set of adjacent
   * sections.
   */
  static int ruptureCount(int sectionCount, int ruptureSize) {
    return sectionCount - ruptureSize + 1;
  }

  private static Map<String, List<Rupture>> denaliCenterEastRuptures() {

    ImmutableMap.Builder<String, List<Rupture>> ruptures = ImmutableMap.builder();

    Map<String, List<Rupture>> grRuptures = createRuptureLists(
        DENALI_CENTER_EAST_GR_MFDS,
        DENALI_CENTER_EAST_SECTIONS,
        DENALI_DIP,
        AK_DEPTH,
        DENALI_WIDTH,
        AK_RAKE);

    Map<String, List<Rupture>> chRuptures = createRuptureLists(
        DENALI_CENTER_EAST_CH_MFDS,
        DENALI_CENTER_EAST_SECTIONS,
        DENALI_DIP,
        AK_DEPTH,
        DENALI_WIDTH,
        AK_RAKE);

    return ruptures
        .putAll(grRuptures)
        .putAll(chRuptures)
        .build();
  }

  private static Map<String, List<Rupture>> denaliCenterTotschundaRuptures() {

    ImmutableMap.Builder<String, List<Rupture>> ruptures = ImmutableMap.builder();

    Map<String, List<Rupture>> grRuptures = createRuptureLists(
        DENALI_CENTER_TOTSCHUNDA_GR_MFDS,
        DENALI_CENTER_TOTSCHUNDA_SECTIONS,
        DENALI_DIP,
        AK_DEPTH,
        DENALI_WIDTH,
        AK_RAKE);

    Map<String, List<Rupture>> chRuptures = createRuptureLists(
        DENALI_CENTER_TOTSCHUNDA_CH_MFDS,
        DENALI_CENTER_TOTSCHUNDA_SECTIONS,
        DENALI_DIP,
        AK_DEPTH,
        DENALI_WIDTH,
        AK_RAKE);

    return ruptures
        .putAll(grRuptures)
        .putAll(chRuptures)
        .build();
  }

  private static Map<String, List<Rupture>> denaliTotschundaRuptures() {
    /*
     * Ensure that identical MFDs were used to for denali-center-east and
     * denali-center-totschunda.
     */
    checkState(DENALI_CENTER_EAST_RUPTURES.size() == DENALI_CENTER_TOTSCHUNDA_RUPTURES.size());

    /*
     * For each magnitude rupture list, create a copy of denali center-east
     * ruptures (many of these rate will be modified) and then create a map from
     * the copy with k:indices string to v:rupture. Create a linked list of
     * center-totschunda ruptures. Loop center-totschunda ruptures. If
     * center-east map contains indices key, add center-totschunda rate to
     * center-east rate and remove center-totschunda rupture from list. Create a
     * new list and all map values. Add remaining ruptures in center-totschunda
     * list to new list.
     */
    ImmutableMap.Builder<String, List<Rupture>> ruptures = ImmutableMap.builder();
    for (String mfdKey : DENALI_CENTER_EAST_RUPTURES.keySet()) {
      List<Rupture> combined = combineRuptures(
          DENALI_CENTER_EAST_RUPTURES.get(mfdKey),
          DENALI_CENTER_TOTSCHUNDA_RUPTURES.get(mfdKey));
      ruptures.put(mfdKey, combined);
    }
    return ruptures.build();
  }

  private static List<Rupture> combineRuptures(List<Rupture> a, List<Rupture> b) {

    /* create indices to rupture map with rupture copies */
    Map<String, Rupture> aMap = FluentIterable
        .from(a)
        .transform(new Function<Rupture, Rupture>() {
          @Override
          public Rupture apply(Rupture rupture) {
            return rupture.copy();
          }
        })
        .uniqueIndex(new Function<Rupture, String>() {
          @Override
          public String apply(Rupture rupture) {
            return rupture.indices;
          }
        });
    List<Rupture> bList = new LinkedList<>(b);
    Iterator<Rupture> bIter = bList.iterator();

    while (bIter.hasNext()) {
      Rupture bRup = bIter.next();
      Rupture aRup = aMap.get(bRup.indices);
      if (aRup != null) {
        aRup.rate += bRup.rate;
        bIter.remove();
      }
    }
    return ImmutableList.<Rupture> builder()
        .addAll(aMap.values())
        .addAll(bList)
        .build();
  }

  private static Map<String, List<Rupture>> castleMountainRuptures() {

    ImmutableMap.Builder<String, List<Rupture>> ruptures = ImmutableMap.builder();

    Map<String, List<Rupture>> grRuptures = createRuptureLists(
        CASTLE_MTN_GR_MFDS,
        CASTLE_MTN_SECTIONS,
        CASTLE_MTN_DIP,
        AK_DEPTH,
        CASTLE_MTN_WIDTH,
        AK_RAKE);

    Map<String, List<Rupture>> chRuptures = createRuptureLists(
        CASTLE_MTN_CH_MFDS,
        CASTLE_MTN_SECTIONS,
        CASTLE_MTN_DIP,
        AK_DEPTH,
        CASTLE_MTN_WIDTH,
        AK_RAKE);

    return ruptures
        .putAll(grRuptures)
        .putAll(chRuptures)
        .build();
  }

  /*
   * Create list of slip rates with same length as a section list for a fault.
   * Indices in the supplied map correspond to trace locations (anchors).
   * 
   * Once a fault has been divided into sections, whichever sections contain the
   * trace points with known slip rates are assigned that slip rate;
   * interpolated slip rates are then assigned to the intermediate sections.
   * 
   * Sections is passed only for final verification that the number of slip
   * values computed equls the number of sections.
   */
  static List<Double> computeSectionSlip(
      LocationList trace,
      List<LocationList> traces,
      Map<Integer, Double> slipMap) {

    /* Actual section length computed as by LocationList.parition(length) */
    double traceLength = trace.length();
    double sectionLength = traceLength / Math.rint(traceLength / TARGET_SECTION_LENGTH);

    /* Interpolated slip rates between each pair of anchors (segment) */
    List<double[]> segmentSlipRates = new ArrayList<>();

    /* List ok; supplied slip map has deterministic iterator. */
    List<Integer> indices = new ArrayList<>(slipMap.keySet());
    for (int i = 0; i < indices.size() - 1; i++) {
      int start = indices.get(i);
      int end = indices.get(i + 1);
      LocationList slipSegment = LocationList.create(
          FluentIterable.from(trace)
              .skip(start)
              .limit(end + 1 - start));
      int slipRateSteps = (int) Math.ceil(slipSegment.length() / sectionLength);
      double[] interpolatedSlips = interpolate(
          slipMap.get(start),
          slipMap.get(end),
          slipRateSteps,
          3);
      segmentSlipRates.add(interpolatedSlips);
    }
    List<Double> slipRates = combine(segmentSlipRates);
    checkState(
        slipRates.size() == traces.size(),
        "Slip rate count (%s) ≠ Section count (%s)",
        slipRates.size(), traces.size());
    return slipRates;
  }

  /*
   * Combines the supplied array, eliminating the duplicate value common to the
   * end of one array and the start of the next.
   */
  static List<Double> combine(List<double[]> data) {
    ImmutableList.Builder<Double> combined = ImmutableList.<Double> builder()
        .addAll(Doubles.asList(data.get(0)));
    for (int i = 1; i < data.size(); i++) {
      combined.addAll(Iterables.skip(Doubles.asList(data.get(i)), 1));
    }
    return combined.build();
  }

  /*
   * TODO consider moving to Data. 'steps' is inclusive of endpoints.
   */
  static double[] interpolate(double min, double max, int steps, int scale) {
    double delta = (max - min) / (steps - 1);
    double[] values = new double[steps];
    values[0] = min;
    for (int i = 1; i < steps - 1; i++) {
      values[i] = min + i * delta;
    }
    values[steps - 1] = max;
    return Data.round(scale, values);
  }

  static double momentRate(List<FaultSection> sections) {
    double moRate = 0.0;
    for (FaultSection section : sections) {
      moRate += momentRate(section);
    }
    return moRate;
  }

  static double momentRate(FaultSection section) {
    double area = section.trace.length() * 1000.0 * section.width * 1000.0;
    return Earthquakes.moment(area, section.slipRate / 1000.0);
  }

  /* ------- MFDs ------- */

  static Map<String, IncrementalMfd> grMfds(
      double mMin,
      double mMax,
      double dMag,
      double b,
      double weight,
      double moRate,
      Uncertainty unc) {

    ImmutableMap.Builder<String, IncrementalMfd> mfds = ImmutableMap.builder();
    for (int i = 0; i < unc.epiCount; i++) {
      // update mMax and nMag
      double mMaxEpi = mMax + unc.epiDeltas[i];
      int nMagEpi = Mfds.magCount(mMin, mMaxEpi, dMag);
      double weightEpi = weight * unc.epiWeights[i];
      // epi branches preserve Mo between mMin and dMag(nMag-1),
      // not mMax to ensure that Mo is 'spent' on earthquakes
      // represented by the epi GR distribution with adj. mMax.
      IncrementalMfd mfd = Mfds.newGutenbergRichterMoBalancedMFD(
          mMin,
          dMag,
          nMagEpi,
          b,
          moRate * weightEpi);
      String key = String.format(
          " GR: mMin=%.2f, %s=%.2f, b=%.2f ",
          mMin, grEpiStrings[i], mMaxEpi, b);
      mfds.put(key, mfd);
    }
    return mfds.build();
  }

  private static Map<String, IncrementalMfd> denaliCenterEastGrMfds() {
    return grMfds(
        AK_GR_M_MIN,
        DENALI_CENTER_EAST_GR_M_MAX,
        AK_M_DELTA,
        AK_B_VALUE,
        AK_GR_WEIGHT,
        DENALI_CENTER_EAST_MO_RATE,
        AK_M_UNCERTAINTY);
  }

  private static Map<String, IncrementalMfd> denaliCenterTotschundaGrMfds() {
    return grMfds(
        AK_GR_M_MIN,
        DENALI_CENTER_TOTSCHUNDA_GR_M_MAX,
        AK_M_DELTA,
        AK_B_VALUE,
        AK_GR_WEIGHT,
        DENALI_CENTER_TOTSCHUNDA_MO_RATE,
        AK_M_UNCERTAINTY);
  }

  private static Map<String, IncrementalMfd> castleMountainGrMfds() {
    return grMfds(
        AK_GR_M_MIN,
        CASTLE_MTN_GR_M_MAX,
        AK_M_DELTA,
        AK_B_VALUE,
        AK_GR_WEIGHT,
        CASTLE_MTN_MO_RATE,
        AK_M_UNCERTAINTY);
  }

  static Map<String, IncrementalMfd> chMfds(
      double m,
      double weight,
      double moRate,
      Uncertainty unc) {

    ImmutableMap.Builder<String, IncrementalMfd> mfds = ImmutableMap.builder();
    for (int i = 0; i < unc.epiCount; i++) {
      double epiMag = m + unc.epiDeltas[i];
      double mfdWeight = weight * unc.epiWeights[i];
      IncrementalMfd mfd = Mfds.newGaussianMoBalancedMFD(
          epiMag,
          unc.aleaSigma,
          unc.aleaCount,
          mfdWeight * moRate,
          true);
      String key = String.format(" CH: %s=%.1f ", chEpiStrings[i], epiMag);
      mfds.put(key, mfd);
    }
    return mfds.build();
  }

  private static Map<String, IncrementalMfd> denaliCenterEastChMfds() {
    return chMfds(
        DENALI_CENTER_EAST_CH_M_MAX,
        AK_CH_WEIGHT,
        DENALI_CENTER_EAST_MO_RATE,
        AK_M_UNCERTAINTY);
  }

  private static Map<String, IncrementalMfd> denaliCenterTotschundaChMfds() {
    return chMfds(
        DENALI_CENTER_TOTSCHUNDA_CH_M_MAX,
        AK_CH_WEIGHT,
        DENALI_CENTER_TOTSCHUNDA_MO_RATE,
        AK_M_UNCERTAINTY);
  }

  private static Map<String, IncrementalMfd> castleMountainChMfds() {
    return chMfds(
        CASTLE_MTN_CH_M,
        AK_CH_WEIGHT,
        CASTLE_MTN_MO_RATE,
        AK_M_UNCERTAINTY);
  }

}
