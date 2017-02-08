package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshm.convert.SystemFaultConverter.SECTION_XML_IN;
import static gov.usgs.earthquake.nshm.convert.SystemFaultConverter.cleanName;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import gov.usgs.earthquake.nshm.convert.SystemFaultConverter.UC3_Filter;

/**
 * Starting point for converting UCERF3 fault system solutions to indexed source
 * models. The grid and fault converters perform the necessary filtering of
 * sources for NSHMP compatibility, as well as reduce rupture rates to account
 * for aftershock removal.
 * 
 * @author Peter Powers
 */
public class SystemConverter {

  private static final Path SRC_DIR = Paths.get("../../svn/OpenSHA/tmp/UC33/src/bravg");
  private static final Path OUT_DIR = Paths.get("models/UCERF3/");

  public static void main(String[] args) throws Exception {
    Path solDir = SRC_DIR.resolve("FM");
    // Path solDir = SRC_DIR.resolve("FM-DM");
    // Path solDir = SRC_DIR.resolve("FM-DM-MS");
    // Path solDir = SRC_DIR.resolve("FM-DM-MS-SS");
    convertUC3(solDir);

    // compareFaultModelIndices();
    // TODO can slip scaling
  }

  static void convertUC3(Path solDir) throws Exception {

    SystemFaultConverter faultConverter = SystemFaultConverter.create();
    SystemGridConverter gridConverter = SystemGridConverter.create();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(solDir, "*.zip")) {
      for (Path path : stream) {
        String fName = path.getFileName().toString();
        UC3_Filter filter = fName.contains("FM31") ? UC3_Filter.FM31 : UC3_Filter.FM32;
        faultConverter.process(path, OUT_DIR, filter);
        gridConverter.process(path, OUT_DIR);

        System.out.println("Conversion complete");
        System.out.println("");
      }
    }
  }

  static double computeWeight(String name) {
    double w = 1.0;

    if (name.contains("FM31")) w *= 0.5;
    if (name.contains("FM32")) w *= 0.5;

    if (name.contains("ABM")) w *= 0.1;
    if (name.contains("GEOL")) w *= 0.3;
    if (name.contains("NEOK")) w *= 0.3;
    if (name.contains("ZENGBB")) w *= 0.3;

    if (name.contains("ELLB")) w *= 0.2;
    if (name.contains("ELLBSL")) w *= 0.2;
    if (name.contains("HB08")) w *= 0.2;
    if (name.contains("SH09M")) w *= 0.2;
    if (name.contains("SHCSD")) w *= 0.2;

    if (name.contains("U2")) w *= 0.5;
    if (name.contains("U3")) w *= 0.5;

    return w;
  }

  /*
   * Combining UC3 fault model branch averaged solutions:
   * 
   * Any FaultModel 3.1 solution will map to its original indices.
   * 
   * Those sections in FaultModel 3.2 that are replicated in 3.1 will be mapped
   * to their 3.1 counterpart index.
   * 
   * Those sections in FaultModel 3.2 but not in 3.1 will be appended to the
   * FaultModel 3.1 section list and their indices mapped to their new indices
   * in the master list.
   * 
   * NOTE becasue inversions were run separately for each fualt model, need to
   * consider that even though section participation may be same for two
   * ruptures, mags, rates and other properties etc. may not be
   */

  static void compareFaultModelIndices() throws Exception {

    BiMap<Integer, String> fm31map = null; // readSections(SOL_DIR,
    // FM31_SOL);
    BiMap<Integer, String> fm32map = null; // readSections(SOL_DIR,
    // FM32_SOL);

    int count = 0;

    // list FM31 entires with FM32 indices, or lack thereof
    for (Entry<Integer, String> entry : fm31map.entrySet()) {
      String masterIdxStr = toString(count);
      String flag31 = "FM31  ";
      String idx31str = toString(entry.getKey());
      Integer idx32 = fm32map.inverse().get(entry.getValue());
      String flag32 = (idx32 != null) ? "FM32  " : "--    ";
      String idx32str = (idx32 != null) ? toString(idx32) : "--    ";
      System.out.println(masterIdxStr + flag31 + idx31str + flag32 + idx32str +
          entry.getValue());
      count++;
    }

    // append FM32 entries missing from FM31
    for (Entry<Integer, String> entry : fm32map.entrySet()) {
      if (fm31map.containsValue(entry.getValue())) continue;
      String masterIdxStr = toString(count);
      String flag31 = "--    ";
      String idx31str = "--    ";
      String flag32 = "FM32  ";
      String idx32str = toString(entry.getKey());
      System.out.println(masterIdxStr + flag31 + idx31str + flag32 + idx32str +
          entry.getValue());
      count++;
    }

  }

  private static String toString(int idx) {
    return Strings.padEnd(Integer.toString(idx), 6, ' ');
  }

  private static BiMap<Integer, String> readSections(String solDirPath, String sol)
      throws ParserConfigurationException, SAXException, IOException {

    ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
    ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document docIn = dBuilder.parse(zip.getInputStream(sectionsEntry));
    docIn.getDocumentElement().normalize();

    zip.close();

    // file in
    Element rootIn = docIn.getDocumentElement();
    NodeList sectsIn = ((Element) rootIn.getElementsByTagName("FaultSectionPrefDataList").item(
        0)).getChildNodes();

    ImmutableBiMap.Builder<Integer, String> builder = ImmutableBiMap.builder();
    for (int i = 0; i < sectsIn.getLength(); i++) {
      Node node = sectsIn.item(i);
      if (!(node instanceof Element)) continue;
      Element sectIn = (Element) node;

      String name = cleanName(sectIn.getAttribute("sectionName"));
      String indexStr = sectIn.getAttribute("sectionId");
      int index = Integer.valueOf(indexStr);

      builder.put(index, name);
    }
    return builder.build();
  }

}
