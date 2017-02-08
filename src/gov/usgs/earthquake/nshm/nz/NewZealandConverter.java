package gov.usgs.earthquake.nshm.nz;

import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.NN;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.NV;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.RV;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.SR;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.SS;
import static org.opensha2.eq.TectonicSetting.ACTIVE_SHALLOW_CRUST;
import static org.opensha2.eq.TectonicSetting.SUBDUCTION_INTERFACE;
import static org.opensha2.eq.TectonicSetting.VOLCANIC;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_FAULT_WC94_LENGTH;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_POINT_WC94_LENGTH;
import static org.opensha2.eq.model.SourceType.FAULT;
import static org.opensha2.eq.model.SourceType.GRID;
import static org.opensha2.eq.model.SourceType.INTERFACE;
import static org.opensha2.eq.model.SourceType.SLAB;
import static org.opensha2.gmm.Gmm.MCVERRY_00_CRUSTAL;
import static org.opensha2.gmm.Gmm.MCVERRY_00_INTERFACE;
import static org.opensha2.gmm.Gmm.MCVERRY_00_SLAB;
import static org.opensha2.gmm.Gmm.MCVERRY_00_VOLCANIC;
import static org.opensha2.internal.Parsing.addAttribute;
import static org.opensha2.internal.Parsing.addElement;
import static org.opensha2.internal.Parsing.enumValueMapToString;
import static org.opensha2.internal.SourceAttribute.DEPTH;
import static org.opensha2.internal.SourceAttribute.DIP;
import static org.opensha2.internal.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha2.internal.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha2.internal.SourceAttribute.NAME;
import static org.opensha2.internal.SourceAttribute.RAKE;
import static org.opensha2.internal.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.internal.SourceAttribute.STRIKE;
import static org.opensha2.internal.SourceAttribute.WEIGHT;
import static org.opensha2.internal.SourceAttribute.WIDTH;
import static org.opensha2.internal.SourceElement.DEFAULT_MFDS;
import static org.opensha2.internal.SourceElement.FAULT_SOURCE_SET;
import static org.opensha2.internal.SourceElement.GEOMETRY;
import static org.opensha2.internal.SourceElement.GRID_SOURCE_SET;
import static org.opensha2.internal.SourceElement.NODE;
import static org.opensha2.internal.SourceElement.NODES;
import static org.opensha2.internal.SourceElement.SETTINGS;
import static org.opensha2.internal.SourceElement.SOURCE;
import static org.opensha2.internal.SourceElement.SOURCE_PROPERTIES;
import static org.opensha2.internal.SourceElement.SUBDUCTION_SOURCE_SET;
import static org.opensha2.internal.SourceElement.TRACE;

import gov.usgs.earthquake.nshm.convert.CH_Data;
import gov.usgs.earthquake.nshm.convert.GMM_Export;
import gov.usgs.earthquake.nshm.convert.GR_Data;
import gov.usgs.earthquake.nshm.nz.NewZealandParser.FaultData;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha2.eq.TectonicSetting;
import org.opensha2.geo.Location;
import org.opensha2.gmm.Gmm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multisets;
import com.google.common.math.DoubleMath;

/*
 * Convert New Zealand source files to NSHM compatible forecast.
 * 
 * @author Peter Powers
 */
@SuppressWarnings("unchecked")
class NewZealandConverter {

  private static final Path FCAST_DIR = Paths.get("models", "nz");
  private static final String GMM_FILE = "gmm.xml";
  private static final String SOURCES_FILE = "sources.xml";

  private NewZealandParser parser;

  private NewZealandConverter() {}

  static NewZealandConverter create() {
    NewZealandConverter nzc = new NewZealandConverter();
    nzc.parser = new NewZealandParser();
    return nzc;
  }

  public static void main(String[] args) throws Exception {
    NewZealandConverter converter = create();
    converter.convertFault(FCAST_DIR);
    converter.convertGrid(FCAST_DIR);

    // gmms get written along with source files; to ensure
    // that updates ocurr if a gmm. file already exists, a
    // gmm file is always created (and possibly overwritten)
    // when each source file is written; this does result in
    // some redundant file writing, but who cares
  }

  static Map<Gmm, Double> volcGmmMap = ImmutableMap.of(MCVERRY_00_VOLCANIC, 1.0);
  static List<Map<Gmm, Double>> volcGmmList = Lists.newArrayList(volcGmmMap);
  static List<Double> volcDistList = Lists.newArrayList(200.0);

  static Map<Gmm, Double> crustGmmMap = ImmutableMap.of(MCVERRY_00_CRUSTAL, 1.0);
  static List<Map<Gmm, Double>> crustGmmList = Lists.newArrayList(crustGmmMap);
  static List<Double> crustDistList = Lists.newArrayList(200.0);

  static Map<Gmm, Double> interGmmMap = ImmutableMap.of(MCVERRY_00_INTERFACE, 1.0);
  static List<Map<Gmm, Double>> interGmmList = Lists.newArrayList(interGmmMap);
  static List<Double> interDistList = Lists.newArrayList(500.0);

  static Map<Gmm, Double> slabGmmMap = ImmutableMap.of(MCVERRY_00_SLAB, 1.0);
  static List<Map<Gmm, Double>> slabGmmList = Lists.newArrayList(slabGmmMap);
  static List<Double> slabDistList = Lists.newArrayList(500.0);

  private void convertFault(Path outDir) throws Exception {

    // faults
    for (TectonicSetting tect : parser.tectonicSettings()) {
      if (tect == VOLCANIC) {

        Path outPath = Paths.get(FAULT.toString(), "volcanic", SOURCES_FILE);
        Path out = outDir.resolve(outPath);
        FaultExporter export = new FaultExporter(false);
        export.faultDataList = parser.getSourceDataList(tect);
        export.setName = "Volcanic Sources";
        export.chRef = CH_Data.create(7.5, 0.0, 1.0, false);
        export.writeXML(out);

        Path gmmPath = Paths.get(FAULT.toString(), "volcanic", GMM_FILE);
        out = outDir.resolve(gmmPath);
        GMM_Export.writeFile(out, volcGmmList, volcDistList, null, null);

      } else if (tect == ACTIVE_SHALLOW_CRUST) {

        Path outPath = Paths.get(FAULT.toString(), "tectonic", SOURCES_FILE);
        Path out = outDir.resolve(outPath);
        FaultExporter export = new FaultExporter(false);
        export.faultDataList = parser.getSourceDataList(tect);
        export.setName = "Tectonic Sources";
        export.chRef = CH_Data.create(7.5, 0.0, 1.0, false);
        export.writeXML(out);

        Path gmmPath = Paths.get(FAULT.toString(), "tectonic", GMM_FILE);
        out = outDir.resolve(gmmPath);
        GMM_Export.writeFile(out, crustGmmList, crustDistList, null, null);

      } else if (tect == SUBDUCTION_INTERFACE) {

        Path outPath = Paths.get(INTERFACE.toString(), SOURCES_FILE);
        Path out = outDir.resolve(outPath);
        FaultExporter export = new FaultExporter(true);
        export.faultDataList = parser.getSourceDataList(tect);
        export.setName = "Interface Sources";
        export.chRef = CH_Data.create(7.5, 0.0, 1.0, false);
        export.writeXML(out);

        Path gmmPath = Paths.get(INTERFACE.toString(), GMM_FILE);
        out = outDir.resolve(gmmPath);
        GMM_Export.writeFile(out, interGmmList, interDistList, null, null);

      } else {
        throw new IllegalStateException(tect.toString());
      }
    }
  }

  private void convertGrid(Path outDir) throws Exception {

    // Set<Double> depthTest = Sets.newHashSet();
    // for (Location loc : parser.locs) {
    // depthTest.add(loc.depth());
    // }
    // System.out.println(depthTest);
    // Set<NZ_SourceID> typeTest = Sets.newHashSet();
    // for (NZ_SourceID id : parser.ids) {
    // typeTest.add(id);
    // }
    // System.out.println(typeTest);

    // loop types and depths
    Set<NZ_SourceID> ids = EnumSet.of(NV, RV, SR, SS, NN);
    Set<Double> depths = ImmutableSet.of(10.0, 30.0, 50.0, 70.0, 90.0);

    /*
     * Split depths at 40km; below = slab, above = crustal | volcanic TODO this
     * may be incorrect; check with Stirling
     * 
     * TODO are slab earthquakes really supposed to be RS
     */

    int count = 0;
    for (NZ_SourceID id : ids) {
      for (double depth : depths) {

        List<Location> locs = new ArrayList<>();
        List<Double> aVals = new ArrayList<>();
        List<Double> bVals = new ArrayList<>();
        List<Double> mMaxs = new ArrayList<>();

        for (int i = 0; i < parser.ids.size(); i++) {
          if (parser.ids.get(i) != id) continue;
          Location loc = parser.locs.get(i);
          if (loc.depth() != depth) continue;
          locs.add(loc);
          aVals.add(parser.aVals.get(i));
          bVals.add(parser.bVals.get(i));
          mMaxs.add(parser.mMaxs.get(i));
          count++;
        }

        if (locs.isEmpty()) continue;

        GridExporter export = new GridExporter();
        export.setName = createGridName(id, depth);
        export.setWeight = 1.0;
        export.id = id;
        export.locs = locs;
        export.aVals = aVals;
        export.bVals = bVals;
        export.mMaxs = mMaxs;
        export.mMin = NewZealandParser.M_MIN;
        Path outPath = createGridPath(id, depth);
        Path out = outDir.resolve(outPath);
        export.writeXML(out);

        Path gmmPath = createGmmPath(id, depth);
        out = outDir.resolve(gmmPath);
        List<Map<Gmm, Double>> gmmMapList = getGmmMapList(id, depth);
        List<Double> distanceList = getDistList(id, depth);
        GMM_Export.writeFile(out, gmmMapList, distanceList, null, null);

      }
    }
    // ensure all lines in parser were processed
    checkState(count == parser.locs.size(), "Only processed %s of %s sources", count,
        parser.locs.size());

  }

  private static final double SLAB_DEPTH_CUT = 40.0;

  private static String createGridName(NZ_SourceID id, double depth) {
    String tect =
        ((depth < SLAB_DEPTH_CUT) ? (id == NV) ? "Volcanic Grid " : "Tectonic Grid " : "Slab ");
    String depthStr = " [" + ((int) depth) + "km";
    String style = (id == NV || id == NN) ? "; normal]" : (id == RV) ? "; reverse]"
        : (id == SR) ? "; reverse-oblique]" : "; strike-slip]";
    return tect + "Sources" + depthStr + style;
  }

  private static Path createGridPath(NZ_SourceID id, double depth) {
    String name = "sources-" + id.toString().toLowerCase() + "-" + ((int) depth) + "km.xml";
    String dir = (depth < SLAB_DEPTH_CUT) ? GRID.toString() : SLAB.toString();
    String subDir = (depth < SLAB_DEPTH_CUT) ? (id == NV) ? "volcanic" : "tectonic" : "";
    return Paths.get(dir, subDir, name);
  }

  private static Path createGmmPath(NZ_SourceID id, double depth) {
    String dir = (depth < SLAB_DEPTH_CUT) ? GRID.toString() : SLAB.toString();
    String subDir = (depth < SLAB_DEPTH_CUT) ? (id == NV) ? "volcanic" : "tectonic" : "";
    return Paths.get(dir, subDir, GMM_FILE);
  }

  private static List<Map<Gmm, Double>> getGmmMapList(NZ_SourceID id, double depth) {
    return (depth < SLAB_DEPTH_CUT) ? (id == NV) ? volcGmmList : crustGmmList : slabGmmList;
  }

  private static List<Double> getDistList(NZ_SourceID id, double depth) {
    return (depth < SLAB_DEPTH_CUT) ? (id == NV) ? volcDistList : crustDistList : slabDistList;
  }

  // returns the most common value
  private static double findDefault(Collection<Double> values) {
    return Multisets.copyHighestCountFirst(HashMultiset.create(values)).iterator().next();
  }

  static class GridExporter {

    String setName;
    double setWeight = 1.0;
    NZ_SourceID id;
    List<Location> locs;
    List<Double> aVals;
    List<Double> bVals;
    List<Double> mMaxs;
    double mMin;

    public void writeXML(Path out) throws ParserConfigurationException, TransformerException,
        IOException {

      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

      // root elements
      Document doc = docBuilder.newDocument();
      doc.setXmlStandalone(true);
      Element root = doc.createElement(GRID_SOURCE_SET.toString());
      addAttribute(NAME, setName, root);
      addAttribute(WEIGHT, setWeight, root);
      doc.appendChild(root);

      writeGrid(root, locs, aVals, bVals, mMaxs, mMin, id);

      // write the content into xml file
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer trans = transformerFactory.newTransformer();
      trans.setOutputProperty(OutputKeys.INDENT, "yes");
      trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      trans.setOutputProperty(OutputKeys.STANDALONE, "yes");
      DOMSource source = new DOMSource(doc);
      Files.createDirectories(out.getParent());
      StreamResult result = new StreamResult(Files.newOutputStream(out));

      trans.transform(source, result);
    }

  }

  // standard grid without customizations requiring incremental MFDs
  private static void writeGrid(Element root, List<Location> locs, List<Double> aVals,
      List<Double> bVals, List<Double> mMaxs, double mMin, NZ_SourceID id) {

    // find most used bVal and mMax
    double bValDefault = findDefault(bVals);
    double mMaxDefault = findDefault(mMaxs);

    GR_Data refGR = GR_Data.create(0.0, bValDefault, mMin, mMaxDefault, 0.1, 1.0);

    Element settings = addElement(SETTINGS, root);
    Element mfdRef = addElement(DEFAULT_MFDS, settings);
    refGR.appendTo(mfdRef, null);
    addSourceProperties(settings, id, locs.get(0).depth());
    Element nodesElem = addElement(NODES, root);

    for (int i = 0; i < locs.size(); i++) {
      Element nodeElem = addElement(NODE, nodesElem);
      nodeElem.setTextContent(Utils.locToString(locs.get(i)));
      GR_Data grDat = GR_Data.create(aVals.get(i), bVals.get(i), mMin, mMaxs.get(i), 0.1, 1.0);
      grDat.addAttributesToElement(nodeElem, refGR);
    }
  }

  // source attribute settings
  private static void addSourceProperties(Element settings, NZ_SourceID id, double depth) {
    Element propsElem = addElement(SOURCE_PROPERTIES, settings);
    addAttribute(MAG_DEPTH_MAP, magDepthDataToString(10.0, new double[] { depth, depth }),
        propsElem);
    addAttribute(FOCAL_MECH_MAP, enumValueMapToString(id.mechWtMap()), propsElem);
    addAttribute(STRIKE, Double.NaN, propsElem);
    addAttribute(RUPTURE_SCALING, NSHM_POINT_WC94_LENGTH, propsElem);
  }

  private static String magDepthDataToString(double mag, double[] depths) {
    StringBuffer sb = new StringBuffer("[");
    if (DoubleMath.fuzzyEquals(depths[0], depths[1], 0.000001)) {
      sb.append("10.0::[").append(depths[0]);
      sb.append(":1.0]]");
    } else {
      sb.append(mag).append("::[");
      sb.append(depths[0]).append(":1.0]; 10.0::[");
      sb.append(depths[1]).append(":1.0]]");
    }
    return sb.toString();
  }

  // TODO might need this
  // private static MagScalingType getScalingRel(String name) {
  // return isCA(name) ? NSHMP_CA : WC_94_LENGTH;
  // }

  static class FaultExporter {

    String setName;
    double setWeight = 1.0;
    List<FaultData> faultDataList;
    boolean subduction;
    CH_Data chRef;

    FaultExporter(boolean subduction) {
      this.subduction = subduction;
    }

    public void writeXML(Path out) throws ParserConfigurationException, TransformerException,
        IOException {

      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

      // root elements
      Document doc = docBuilder.newDocument();
      doc.setXmlStandalone(true);
      Element root = doc.createElement(subduction ? SUBDUCTION_SOURCE_SET.toString()
          : FAULT_SOURCE_SET.toString());
      doc.appendChild(root);
      addAttribute(NAME, setName, root);
      addAttribute(WEIGHT, setWeight, root);

      // reference MFDs and uncertainty
      Element settings = addElement(SETTINGS, root);
      Element mfdRef = addElement(DEFAULT_MFDS, settings);
      chRef.appendTo(mfdRef, null);

      // source properties
      Element propsElem = addElement(SOURCE_PROPERTIES, settings);
      addAttribute(RUPTURE_SCALING, NSHM_FAULT_WC94_LENGTH, propsElem);

      for (FaultData fault : faultDataList) {

        Element src = addElement(SOURCE, root);
        addAttribute(NAME, fault.name, src);

        // single mfd
        CH_Data mfdDat = CH_Data.create(fault.mag, fault.rate, 1.0, false);
        mfdDat.appendTo(src, chRef);

        // append geometry from first entry
        Element geom = addElement(GEOMETRY, src);
        addAttribute(DIP, fault.dip, geom);
        addAttribute(WIDTH, fault.width, "%.3f", geom);
        addAttribute(RAKE, fault.rake, geom);
        addAttribute(DEPTH, fault.zTop, geom);
        Element trace = addElement(TRACE, geom);
        trace.setTextContent(fault.trace.toString());
      }

      // write the content into xml file
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      Transformer trans = transformerFactory.newTransformer();
      trans.setOutputProperty(OutputKeys.INDENT, "yes");
      trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      trans.setOutputProperty(OutputKeys.STANDALONE, "yes");
      DOMSource source = new DOMSource(doc);
      Files.createDirectories(out.getParent());
      StreamResult result = new StreamResult(Files.newOutputStream(out));

      trans.transform(source, result);
    }
  }

}
