package gov.usgs.earthquake.nshm.cous2018;

import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.NORMAL;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.REVERSE;
import static gov.usgs.earthquake.nshmp.eq.fault.FocalMech.STRIKE_SLIP;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addAttribute;
import static gov.usgs.earthquake.nshmp.internal.Parsing.addElement;
import static gov.usgs.earthquake.nshmp.internal.Parsing.enumValueMapToString;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.FOCAL_MECH_MAP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.ID;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAG_DEPTH_MAP;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.MAX_DEPTH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.NAME;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.PATH;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.RUPTURE_SCALING;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.STRIKE;
import static gov.usgs.earthquake.nshmp.internal.SourceAttribute.WEIGHT;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.DEFAULT_MFDS;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.GRID_SOURCE_SET;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.NODES;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.SETTINGS;
import static gov.usgs.earthquake.nshmp.internal.SourceElement.SOURCE_PROPERTIES;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import gov.usgs.earthquake.nshm.convert.GR_Data;
import gov.usgs.earthquake.nshmp.eq.fault.FocalMech;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureScaling;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureSurface;
import gov.usgs.earthquake.nshmp.eq.model.Distance;
import gov.usgs.earthquake.nshmp.eq.model.FaultSource;
import gov.usgs.earthquake.nshmp.eq.model.HazardModel;
import gov.usgs.earthquake.nshmp.eq.model.Source;
import gov.usgs.earthquake.nshmp.eq.model.SourceSet;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.Locations;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.util.Maths;

@SuppressWarnings("javadoc")
public class WusGridProcessor {

  /*
   * Developer notes:
   * 
   * WUS
   * 
   * Read agrid values from 6 files (3 catalogs, 2 smoothing kernels) and parse
   * them into their own files.
   *
   * Process the fault source model to derive mMax for GR and CH branches.
   * 
   * WUS mMax truncation: when processing the CH branch, we want grid mMax to be
   * the largest possible bin center less than CH-mMax. By using HALF_DOWN
   * rounding the bin-center below mMax=7.25 will be 7.15.
   * 
   * within 10km of strike-slip; dip>85; rRUP
   * 
   * over dipping faults
   * 
   * Carve out CA
   * 
   * Using modified WUS Fault inputs:
   * 
   * Only "Geologic Model Full Rupture" is enabled
   * 
   * Epistemic and aleatory magnitude uncertainty are diabled
   * 
   * GR MFDs and low-weight central and southern strands removed from Seattle.
   * 
   * Falsified floats atribute so only single rupture is created.
   * 
   * Commented out Saddle Mountain, which has two SINGLE MFDs
   * 
   * Only processing 50deg dip variant - arguably, the 35 degree dip variant
   * will dominate but this than may supress hazard in grid cells not
   * intersected by the 65 dip variant
   * 
   */

  public static void main(String[] args) throws Exception {
    run();
  }

  static final Path OUT = Paths.get("tmp/nshm");
  static final Path WUS_OUT = OUT.resolve("wus");

  static final Path MMAX_REF_MODEL = Paths.get("models/2018-mMax/");

  static final Path CAT_PATH = Paths.get("../nshmp-haz-catalogs/2018");
  static final Path AGRID_PATH = CAT_PATH.resolve("agrids");

  static final Path UCERF3_CLIP = AGRID_PATH.resolve("wus-ucerf3-clip.geojson");

  static final int GRID_ID = -1;

  static final Map<String, String> AGRIDS = ImmutableMap.<String, String> builder()
      .put("wus-fixed-ext.csv", "ext-fixed")
      .put("wus-fixed-cmp.csv", "cmp-fixed")
      .put("wus-fixed-cmp-nopuget.csv", "cmp-nopuget-fixed")
      .put("wus-adaptive-ext.csv", "ext-adapt")
      .put("wus-adaptive-cmp.csv", "cmp-adapt")
      .put("wus-adaptive-cmp-nopuget.csv", "cmp-nopuget-adapt")
      .build();

  static void run() throws Exception {
    HazardModel model = HazardModel.load(MMAX_REF_MODEL);

    // StreamSupport.stream(model.spliterator(), false)
    // .flatMap(ss -> StreamSupport.stream(ss.spliterator(), false))
    // // .limit(2)
    // .peek(System.out::println)
    // .forEach(WusGridProcessor::printRupture);

    // Location testSite = Location.create(-117.0, 34.0);

    // OptionalDouble mMax = StreamSupport.stream(model.spliterator(), false)
    // .flatMap(sourceSet -> StreamSupport.stream(sourceSet.spliterator(),
    // false))
    // .filter(new SourceFilter(testSite))
    // .mapToDouble(source -> source.mfds().get(0).x(0))
    // .min();

    AGRIDS.entrySet().stream()
//        .limit(1)
        .forEach(entry -> writeSource(
            entry.getKey(),
            entry.getValue(),
            model));

    // writeSource(src, dest, name, weight);
  }

  static void processSourceSet(SourceSet sourceSet, Location site) {

  }

  static class SourceFilter implements Predicate<Source> {

    final Location reference;

    SourceFilter(Location reference) {
      this.reference = reference;
    }

    @Override
    public boolean test(Source source) {
      FaultSource fs = (FaultSource) source;

      // only keep 50° variants
      if (fs.name().endsWith(" 35") || fs.name().endsWith(" 65")) {
        return false;
      }
      // coarse filter
      double coarseR = Locations.distanceToSegmentFast(
          fs.trace.first(),
          fs.trace.last(),
          reference);
      if (coarseR > 40) {
        return false;
      }
      // fine filter
      RuptureSurface surface = Iterables.getOnlyElement(fs).surface();
      Distance distances = surface.distanceTo(reference);
      if (surface.dip() > 85.0) {
        return distances.rRup < 10.0;
      }
      return distances.rJB < 0.01;
    }

  }

  static void printRupture(Source source) {
    int size = Iterables.size(source);
    if (size > 1) {
      System.out.println("Ruptures: " + size);
      throw new IllegalStateException("Only singleton ruptures permitted");
    }
    System.out.println(Iterables.getOnlyElement(source).surface().getClass());
    // StreamSupport.stream(source.spliterator(), false)
    // .mapToInt(Iterables::)
    // .forEach(System.out::println);

  }

  /* Success conditioned on knowing sources only have 1 MFD */
  static double sourceMax(Source source) {
    return source.mfds().get(0).x(0);
  }

  static void printMfds(Source source) {
    int size = Iterables.size(source.mfds());
    if (size > 1) {
      System.out.println("MFDs: " + size);
      throw new IllegalStateException("Only singleton MFDs permitted");
    }
    // source.mfds().stream().forEach(System.out::println);
  }

  static List<MwMaxNode> createNodeList(Path agrid, HazardModel model) throws IOException {
    List<MwMaxNode> nodes = Files.readAllLines(agrid).stream()
        .map(new LineToMwMaxNode())
        .sorted()
        .filter(node -> node.a > 0.0)
        .collect(Collectors.toList());

    nodes.parallelStream().forEach(node -> addMwMax(model, node));
    return nodes;
  }

  /*
   * Adds the characteristic mMax
   */
  static MwMaxNode addMwMax(HazardModel model, MwMaxNode node) {
    OptionalDouble mMax = StreamSupport.stream(model.spliterator(), false)
        .flatMap(sourceSet -> StreamSupport.stream(
            sourceSet.spliterator(),
            false))
        .filter(new SourceFilter(node.loc))
        .mapToDouble(source -> source.mfds().get(0).x(0))
        .min();
//    if (mMax.isPresent() && mMax.getAsDouble() < M_MAX) {
//      System.out.println(mMax.getAsDouble());
//    }
    node.mMax = mMax.isPresent()
        ? Double.min(mMax.getAsDouble(), M_MAX)
        : M_MAX;
    return node;
  }

  /********** XML *********/

  static void writeSource(String src, String dest, HazardModel model) {

    try {

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer trans = transformerFactory.newTransformer();
      trans.setOutputProperty(OutputKeys.INDENT, "yes");
      trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

      Stopwatch sw = Stopwatch.createStarted();
      System.out.println("Starting mMax processing: " + src);
      List<MwMaxNode> nodes = createNodeList(AGRID_PATH.resolve(src), model);
      System.out.println("  Created nodes: " + sw);

      Map<FocalMech, Double> mechMap = dest.contains("cmp")
          ? CMP_MECH_WT_MAP
          : EXT_MECH_WT_MAP;

      Path gridOut = WUS_OUT.resolve(SourceType.GRID.toString());
      Path nodesOut = gridOut.resolve("sources");
      Files.createDirectories(gridOut);
      Files.createDirectories(nodesOut);

      String chDest = dest + "-ch";
      Path chXml = gridOut.resolve(chDest + ".xml");
      Path chCsv = nodesOut.resolve(chDest + ".csv");
      Document chDoc = createDocument();
      Element chRoot = addRootElement(chDoc, chDest, GRID_ID, calcWeight(chDest));
      addGridXml(chRoot, chCsv, mechMap);
      DOMSource chSource = new DOMSource(chDoc);
      StreamResult chResult = new StreamResult(chXml.toFile());
      trans.transform(chSource, chResult);
      writeChNodeCsv(chCsv, nodes);
      System.out.println("  Full ruptures: " + sw);

      String grDest = dest + "-gr";
      Path grXml = gridOut.resolve(grDest + ".xml");
      Path grCsv = nodesOut.resolve(grDest + ".csv");
      Document grDoc = createDocument();
      Element grRoot = addRootElement(grDoc, grDest, GRID_ID, calcWeight(grDest));
      addGridXml(grRoot, grCsv, mechMap);
      DOMSource grSource = new DOMSource(grDoc);
      StreamResult grResult = new StreamResult(grXml.toFile());
      trans.transform(grSource, grResult);
      writeGrNodeCsv(grCsv, nodes);
      System.out.println("  Partial ruptures: " + sw);

      String m8Dest = dest + "-m8";
      Path m8Xml = gridOut.resolve(m8Dest + ".xml");
      Path m8Csv = nodesOut.resolve(m8Dest + ".csv");
      Document m8Doc = createDocument();
      Element m8Root = addRootElement(m8Doc, m8Dest, GRID_ID, calcWeight(m8Dest));
      addGridXml(m8Root, m8Csv, mechMap);
      DOMSource m8Source = new DOMSource(m8Doc);
      StreamResult m8Result = new StreamResult(m8Xml.toFile());
      trans.transform(m8Source, m8Result);
      writeM8NodeCsv(m8Csv, nodes);
      System.out.println("  M8 ruptures: " + sw.stop());

    } catch (ParserConfigurationException | TransformerException | IOException e) {
      e.printStackTrace();
    }
  }

  static Element addRootElement(Document doc, String name, int id, double weight) {
    Element root = doc.createElement(GRID_SOURCE_SET.toString());
    addAttribute(NAME, name, root);
    addAttribute(ID, id, root);
    addAttribute(WEIGHT, weight, root);
    Util.addDisclaimer(root);
    doc.appendChild(root);
    return root;
  }

  static Document createDocument() throws ParserConfigurationException {
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.newDocument();
    doc.setXmlStandalone(true);
    return doc;
  }

  static final double A = 0.0;
  static final double B = 0.8;
  static final double M_MIN = 5.05;
  static final double M_MAX = 7.45;
  static final double GR_M_CLIP = 6.45;
  static final double M8_MAX = 7.95;
  static final double D_MAG = 0.1;
  static final double C_MAG = 7.5;

  static final double[] DEPTHS = { 5.0, 1.0 };
  static final double WUS_DEPTH_MAG = 6.5;
  static final double WUS_MAX_DEPTH = 14.0;

  static final Map<FocalMech, Double> CMP_MECH_WT_MAP = ImmutableMap.of(
      STRIKE_SLIP, 0.333,
      NORMAL, 0.0,
      REVERSE, 0.667);

  static final Map<FocalMech, Double> EXT_MECH_WT_MAP = ImmutableMap.of(
      STRIKE_SLIP, 0.333,
      NORMAL, 0.667,
      REVERSE, 0.0);

  static final RuptureScaling WUS_RUPTURE_SCALING = RuptureScaling.NSHM_POINT_WC94_LENGTH;
  static final double WUS_STRIKE = Double.NaN;

  /* Add grid XML elements to root. */
  static void addGridXml(Element root, Path gridpath, Map<FocalMech, Double> mechMap) {
    GR_Data grDat = GR_Data.create(A, B, M_MIN, M_MAX, D_MAG, 1.0);
    if (gridpath.getFileName().toString().contains("-m8")) {
      grDat.cMag = C_MAG;
    }
    Element settings = addElement(SETTINGS, root);
    Element mfdRef = addElement(DEFAULT_MFDS, settings);
    grDat.appendTo(mfdRef, null);
    addWusSourceProperties(settings, mechMap);
    Element nodesElem = addElement(NODES, root);
    addAttribute(PATH, gridpath.getFileName().toString(), nodesElem);
  }

  private static void addWusSourceProperties(Element settings, Map<FocalMech, Double> mechMap) {
    Element propsElem = addElement(SOURCE_PROPERTIES, settings);
    addAttribute(MAG_DEPTH_MAP, Util.magDepthDataToString(WUS_DEPTH_MAG, DEPTHS),
        propsElem);
    addAttribute(MAX_DEPTH, WUS_MAX_DEPTH, propsElem);
    addAttribute(FOCAL_MECH_MAP, enumValueMapToString(mechMap), propsElem);
    addAttribute(STRIKE, WUS_STRIKE, propsElem);
    addAttribute(RUPTURE_SCALING, WUS_RUPTURE_SCALING, propsElem);
  }

  private static double calcWeight(String name) {
    double smoothingWt = name.contains("fixed") ? 0.6 : 0.4;
    double mfdWt = 0.0;
    if (name.contains("gr")) {
      mfdWt = 0.9 * (name.contains("ext") ? 0.333 : 0.5);
    } else if (name.contains("ch")) {
      mfdWt = 0.9 * (name.contains("ext") ? 0.667 : 0.5);
    } else {
      mfdWt = 0.1;
    }
    double tectWt = name.contains("cmp") ? 0.5 : 1.0;
    double wt = smoothingWt * mfdWt * tectWt;
    return Maths.round(wt, 6);
  }

  static void writeChNodeCsv(Path out, List<MwMaxNode> nodes) throws IOException {
    Files.write(out, MwMaxNode.HEADER.getBytes());
    List<String> lines = nodes.stream()
        .map(MwMaxNode::toChString)
        .collect(Collectors.toList());
    Files.write(out, lines, StandardOpenOption.APPEND);
  }

  static void writeGrNodeCsv(Path out, List<MwMaxNode> nodes) throws IOException {
    Files.write(out, MwMaxNode.HEADER.getBytes());
    List<String> lines = nodes.stream()
        .map(MwMaxNode::toGrString)
        .collect(Collectors.toList());
    Files.write(out, lines, StandardOpenOption.APPEND);
  }

  static void writeM8NodeCsv(Path out, List<MwMaxNode> nodes) throws IOException {
    Files.write(out, Node.HEADER.getBytes());
    List<String> lines = nodes.stream()
        .map(MwMaxNode::toM8String)
        .collect(Collectors.toList());
    Files.write(out, lines, StandardOpenOption.APPEND);
  }

  static class MwMaxNode extends Node {

    static final String HEADER = "lon,lat,type,mmax,a" + LINE_SEPARATOR.value();
    static final String FORMAT = "%.1f,%.1f,GR,%.2f,%.8g";
    static final String FORMAT_M8 = "%.1f,%.1f,GR_TAPER,%.8g";

    double mMax;

    String toChString() {
      return String.format(FORMAT, loc.lon(), loc.lat(), mMax, a);
    }

    String toGrString() {
      double grMax = mMax < M_MAX ? GR_M_CLIP : mMax;
      return String.format(FORMAT, loc.lon(), loc.lat(), grMax, a);
    }

    String toM8String() {
      return String.format(FORMAT_M8, loc.lon(), loc.lat(), a);
    }

  }

  static class LineToMwMaxNode implements Function<String, MwMaxNode> {

    @Override
    public MwMaxNode apply(String line) {
      List<Double> d = Parsing.splitToDoubleList(line, Delimiter.COMMA);
      MwMaxNode node = new MwMaxNode();
      node.loc = Location.create(d.get(1), d.get(0));
      node.a = d.get(2);
      return node;
    }
  }

}
