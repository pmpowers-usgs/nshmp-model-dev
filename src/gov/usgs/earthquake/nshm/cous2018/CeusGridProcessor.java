package gov.usgs.earthquake.nshm.cous2018;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import gov.usgs.earthquake.nshm.convert.GR_Data;
import gov.usgs.earthquake.nshmp.eq.fault.FocalMech;
import gov.usgs.earthquake.nshmp.eq.fault.surface.RuptureScaling;
import gov.usgs.earthquake.nshmp.eq.model.SourceType;
import gov.usgs.earthquake.nshmp.geo.Location;
import gov.usgs.earthquake.nshmp.geo.Region;
import gov.usgs.earthquake.nshmp.internal.Parsing;
import gov.usgs.earthquake.nshmp.internal.Parsing.Delimiter;
import gov.usgs.earthquake.nshmp.json.Feature;
import gov.usgs.earthquake.nshmp.json.FeatureCollection;
import gov.usgs.earthquake.nshmp.json.Polygon;
import gov.usgs.earthquake.nshmp.json.Properties;

@SuppressWarnings("javadoc")
public class CeusGridProcessor {

  /*
   * Developer notes:
   * 
   * CEUS
   * 
   * Read agrid values from 2 files (adaptive and fixed) and parse them into
   * zone csv files
   * 
   * Write the xml wrappers for the zone files
   * 
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
   */

  private static final BigDecimal MFD_HALF_BIN_WIDTH = BigDecimal.valueOf(0.05);

  static double round(double m) {
    return BigDecimal.valueOf(m)
        .setScale(1, RoundingMode.HALF_DOWN)
        .subtract(MFD_HALF_BIN_WIDTH)
        .doubleValue();
  }

  static final Path OUT = Paths.get("tmp/nshm");
  static final Path CEUS_OUT = OUT.resolve("ceus");

  static final Path CAT_PATH = Paths.get("../nshmp-haz-catalogs/2018");
  static final Path AGRID_PATH = CAT_PATH.resolve("agrids");

  static final Path CEUS_FIXED = AGRID_PATH.resolve("ceus-fixed.csv");
  static final Path CEUS_ADAPT = AGRID_PATH.resolve("ceus-adaptive.csv");

  static final double ADAPT_WT = 0.4;
  static final double FIXED_WT = 0.6;

  static final double USGS_WT = 0.5;
  static final double SSCN_WT = 0.5;

  static final Path ZONE_PATH = CAT_PATH.resolve("zones");
  static final Path ZONES_USGS = ZONE_PATH.resolve("USGS.geojson");
  static final Path ZONES_SSCN = ZONE_PATH.resolve("SSCn.geojson");

  static final Map<String, Zone> usgsZones = initCeusZones(ZONES_USGS, "USGS");
  static final Map<String, Zone> sscnZones = initCeusZones(ZONES_SSCN, "SSCn");

  /* Create map from CEUS mMax zone file. */
  private static Map<String, Zone> initCeusZones(Path geojson, String label) {
    try {
      System.out.println("CEUS " + label + " zones:");
      FeatureCollection fc = FeatureCollection.read(geojson);
      ImmutableMap.Builder<String, Zone> zoneMap = ImmutableMap.builder();

      for (Feature feature : fc) {
        Properties properties = feature.getProperties();
        String name = properties.getStringProperty("title");
        int id = properties.getIntProperty("id");
        MMaxData mMaxData = properties.getProperty(MMaxData.class);

        Polygon poly = feature.getGeometry().asPolygon();
        Region region = poly.toRegion(name);

        Zone zone = new Zone();
        zone.id = id;
        zone.region = region;
        zone.mMax = mMaxData.toMap();

        zoneMap.put(name, zone);

        System.out.println("  " + zone.id + ": " + zone.mMax + " " + name);
      }
      System.out.println();
      return zoneMap.build();

    } catch (IOException ioe) {
      ioe.printStackTrace();
      return null;
    }
  }

  /**
   * Container class to represent the mMax property in each {@link Feature}.
   * <br><br>
   * 
   * mMax json structure:
   * 
   * <pre>
   *  "mMax": [
   *    {
   *      "id": ,
   *      "Mw": ,
   *      "weight": 
   *    }
   *  ]
   * </pre>
   */
  static class MMaxData {
    List<MMaxAttributes> mMax;

    /**
     * Return a {@code Map<Double, Double>} representing a
     * {@code Map<Mw, weight>}.
     * 
     * @return The map of Mw and weight
     */
    Map<Double, Double> toMap() {
      ImmutableMap.Builder<Double, Double> mMaxMap = ImmutableMap.builder();
      mMax.stream().forEach(data -> mMaxMap.put(data.Mw, data.weight));
      return mMaxMap.build();
    }
  }

  /**
   * Container class to represent the attributes in the mMax {@link Feature}
   * property.
   */
  static class MMaxAttributes {
    int id;
    double Mw;
    double weight;
  }

  static void runCeus() throws IOException {

    Path gridOut = CEUS_OUT.resolve(SourceType.GRID.toString());
    Path nodesOut = gridOut.resolve("sources");

    // set adaptive vs fixed
    String folder = "usgs-fixed";
    Map<String, List<Node>> usgsFixed = processZones(CEUS_FIXED, usgsZones);
    writeNodes(usgsFixed, nodesOut.resolve(folder));
    writeSources(usgsZones, gridOut.resolve(folder), folder, USGS_WT * FIXED_WT);

    folder = "usgs-adapt";
    Map<String, List<Node>> usgsAdapt = processZones(CEUS_ADAPT, usgsZones);
    writeNodes(usgsAdapt, nodesOut.resolve(folder));
    writeSources(usgsZones, gridOut.resolve(folder), folder, USGS_WT * ADAPT_WT);

    folder = "sscn-fixed";
    Map<String, List<Node>> sscnFixed = processZones(CEUS_FIXED, sscnZones);
    writeNodes(sscnFixed, nodesOut.resolve(folder));
    writeSources(sscnZones, gridOut.resolve(folder), folder, SSCN_WT * FIXED_WT);

    folder = "sscn-adapt";
    Map<String, List<Node>> sscnAdapt = processZones(CEUS_ADAPT, sscnZones);
    writeNodes(sscnAdapt, nodesOut.resolve(folder));
    writeSources(sscnZones, gridOut.resolve(folder), folder, SSCN_WT * ADAPT_WT);
  }

  static void writeSources(Map<String, Zone> zoneMap, Path out, String folder, double weight)
      throws IOException {
    try {
      for (Entry<String, Zone> entry : zoneMap.entrySet()) {
        writeSource(entry.getKey(), entry.getValue(), out, folder, weight);
      }
    } catch (Exception e) {
      // ParserConfigurationException
      // TransformerConfigurationException
      // TransformerException
      throw new IOException(e);
    }
  }

  static void writeSource(String name, Zone zone, Path out, String folder, double weight)
      throws ParserConfigurationException,
      TransformerException,
      IOException {

    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    // root elements
    Document doc = docBuilder.newDocument();
    doc.setXmlStandalone(true);
    Element root = doc.createElement(GRID_SOURCE_SET.toString());
    addAttribute(NAME, name, root);
    addAttribute(ID, zone.id, root);
    addAttribute(WEIGHT, weight, root);
    Util.addDisclaimer(root);
    doc.appendChild(root);
    writeZoneGrid(root, zone, folder);

    // write the content into xml file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer trans = transformerFactory.newTransformer();
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

    DOMSource source = new DOMSource(doc);
    Files.createDirectories(out);
    Path sourcepath = out.resolve(name + ".xml");
    StreamResult result = new StreamResult(sourcepath.toFile());

    trans.transform(source, result);
  }

  static final double A = 0.0;
  static final double B = 1.0;
  static final double M_MIN = 4.75;
  static final double M_MAX = 7.45;
  static final double D_MAG = 0.1;

  static final double[] DEPTHS = { 5.0, 5.0 };
  static final double CEUS_DEPTH_MAG = 6.5;
  static final double CEUS_MAX_DEPTH = 22.0;
  static final Map<FocalMech, Double> CEUS_MECH_WT_MAP = Maps.immutableEnumMap(
      ImmutableMap.of(
          STRIKE_SLIP, 1.0,
          REVERSE, 0.0,
          NORMAL, 0.0));
  static final RuptureScaling CEUS_RUPTURE_SCALING = RuptureScaling.NSHM_SOMERVILLE;
  static final double CEUS_STRIKE = Double.NaN;

  // standard grid without customizations requiring incremental MFDs
  static void writeZoneGrid(Element root, Zone zone, String folder) {

    GR_Data grDat = GR_Data.create(A, B, M_MIN, M_MAX, D_MAG, 1.0);

    Element settings = addElement(SETTINGS, root);
    Element mfdRef = addElement(DEFAULT_MFDS, settings);
    for (Entry<Double, Double> entry : zone.mMax.entrySet()) {
      grDat.mMax = entry.getKey() - grDat.dMag / 2.0;
      grDat.weight = entry.getValue();
      grDat.appendTo(mfdRef, null);
    }
    addCeusSourceProperties(settings);
    Element nodesElem = addElement(NODES, root);
    String gridpath = Paths.get(folder, zone.region.name() + ".csv").toString();
    addAttribute(PATH, gridpath, nodesElem);
  }

  private static void addCeusSourceProperties(Element settings) {
    Element propsElem = addElement(SOURCE_PROPERTIES, settings);
    addAttribute(MAG_DEPTH_MAP, Util.magDepthDataToString(CEUS_DEPTH_MAG, DEPTHS), propsElem);
    addAttribute(MAX_DEPTH, CEUS_MAX_DEPTH, propsElem);
    addAttribute(FOCAL_MECH_MAP, enumValueMapToString(CEUS_MECH_WT_MAP), propsElem);
    addAttribute(STRIKE, CEUS_STRIKE, propsElem);
    addAttribute(RUPTURE_SCALING, CEUS_RUPTURE_SCALING, propsElem);
  }

  static void writeNodes(Map<String, List<Node>> nodesMap, Path out) throws IOException {
    Files.createDirectories(out);
    for (Entry<String, List<Node>> entry : nodesMap.entrySet()) {
      Path file = out.resolve(entry.getKey() + ".csv");
      Files.write(file, Node.HEADER.getBytes());
      Files.write(
          file,
          entry.getValue().stream()
              .map(Node::toString)
              .collect(Collectors.toList()),
          StandardOpenOption.APPEND);
    }
  }

  static Map<String, List<Node>> processZones(
      Path agrid,
      Map<String, Zone> zones) throws IOException {

    return Files.readAllLines(agrid).stream()
        .map(new LineToNode())
        .sorted()
        .filter(node -> node.a > 0.0)
        .collect(Collectors.groupingBy(node -> ceusZone(zones, node)));
  }

  static String ceusZone(Map<String, Zone> zoneMap, Node node) {
    for (Entry<String, Zone> entry : zoneMap.entrySet()) {
      if (entry.getValue().region.contains(node.loc)) {
        return entry.getKey();
      }
    }
    throw new IllegalArgumentException("Node not in zone: " + node);
  }

  public static void main(String[] args) throws IOException {
    runCeus();
  }

  static class Zone {
    int id;
    Region region;
    Map<Double, Double> mMax;
  }

  static class LineToNode implements Function<String, Node> {

    @Override
    public Node apply(String line) {
      List<Double> d = Parsing.splitToDoubleList(line, Delimiter.COMMA);
      Node node = new Node();
      node.loc = Location.create(d.get(1), d.get(0));
      node.a = d.get(2);
      return node;
    }
  }

}
