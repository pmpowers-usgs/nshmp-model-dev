package gov.usgs.earthquake.nshm.ak;

import static gov.usgs.earthquake.nshm.ak.VariableSlipUtil.*;

import static org.opensha2.internal.Parsing.addAttribute;
import static org.opensha2.internal.Parsing.addComment;
import static org.opensha2.internal.Parsing.addElement;
import static org.opensha2.internal.SourceAttribute.ID;
import static org.opensha2.internal.SourceAttribute.NAME;
import static org.opensha2.internal.SourceAttribute.WEIGHT;
import static org.opensha2.internal.SourceElement.DEFAULT_MFDS;
import static org.opensha2.internal.SourceElement.SETTINGS;
import static org.opensha2.internal.SourceElement.SYSTEM_FAULT_SECTIONS;
import static org.opensha2.internal.SourceElement.SYSTEM_SOURCE_SET;

import org.opensha2.internal.MathUtils;
import org.opensha2.internal.Parsing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import gov.usgs.earthquake.nshm.convert.CH_Data;

/*
 * Build system-style sources for AK faults with along-strike slip variability.
 * See AkData for raw data imported from 2007 Alaska fortran; see AkDataReduced
 * for derived fault sections, MFDs, moment rates, etc...
 *
 * @author Peter Powers
 */
class SystemCreator {

  static final Path OUT_DIR = Paths.get("models/AK2007/System/");
  static final String SECTION_XML_OUT = "fault_sections.xml";
  static final String RUPTURES_XML_OUT = "fault_ruptures.xml";
  
  static final String CASTLE_MTN_NAME = "Castle Mountain Fault";
  static final String DENALI_TOTSCHUNDA_NAME = "Denali – Totschunda System";
  
  static final String REF = "AKF2.out_revF.in, AKF3.out_revF.in";

  public static void main(String[] args) throws Exception {

    writeSections(CASTLE_MTN_NAME, REF, CASTLE_MTN_SECTIONS);
    writeRuptures(CASTLE_MTN_NAME, -1, 1.0, REF, CASTLE_MTN_RUPTURES);

    writeSections(DENALI_TOTSCHUNDA_NAME + " (CE)", REF, DENALI_CENTER_EAST_SECTIONS);
    writeRuptures(DENALI_TOTSCHUNDA_NAME + " (CE)", -1, 1.0, REF, DENALI_CENTER_EAST_RUPTURES);

    writeSections(DENALI_TOTSCHUNDA_NAME + " (CT)", REF, DENALI_CENTER_TOTSCHUNDA_SECTIONS);
    writeRuptures(DENALI_TOTSCHUNDA_NAME + " (CT)", -1, 1.0, REF, DENALI_CENTER_TOTSCHUNDA_RUPTURES);
    
    writeSections(DENALI_TOTSCHUNDA_NAME, REF, DENALI_TOTSCHUNDA_SECTIONS);
    writeRuptures(DENALI_TOTSCHUNDA_NAME, -1, 1.0, REF, DENALI_TOTSCHUNDA_RUPTURES);
    
  }

  static void writeSections(
      String name,
      String reference,
      List<FaultSection> sections)
      throws IOException, ParserConfigurationException, TransformerException {

    Path dirOut = OUT_DIR.resolve(name);
    Files.createDirectories(dirOut);
    File out = dirOut.resolve(SECTION_XML_OUT).toFile();

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

    // file out
    Document docOut = dBuilder.newDocument();
    Element root = docOut.createElement(SYSTEM_FAULT_SECTIONS.toString());
    docOut.appendChild(root);
    addAttribute(NAME, name, root);
    addDisclaimer(root);
    addComment(" Reference: " + reference + " ", root);

    for (FaultSection section : sections) {
      section.appendTo(root);
    }

    TransformerFactory transFactory = TransformerFactory.newInstance();
    Transformer trans = transFactory.newTransformer();
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    DOMSource source = new DOMSource(docOut);

    StreamResult result = new StreamResult(out);
    trans.transform(source, result);
  }

  static void writeRuptures(
      String name,
      int id,
      double weight,
      String reference,
      Map<String, List<Rupture>> ruptureMap)
      throws IOException, ParserConfigurationException, TransformerException {

    Path dirOut = OUT_DIR.resolve(name);
    Files.createDirectories(dirOut);
    File out = dirOut.resolve(RUPTURES_XML_OUT).toFile();

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

    // file out
    Document docOut = dBuilder.newDocument();
    Element root = docOut.createElement(SYSTEM_SOURCE_SET.toString());
    docOut.appendChild(root);
    addAttribute(NAME, name, root);
    addAttribute(WEIGHT, weight, root);
    addAttribute(ID, id, root);
    addDisclaimer(root);
    addComment(" Reference: " + reference + " ", root);

    // settings and defaults
    Element settings = addElement(SETTINGS, root);
    Element mfdRef = addElement(DEFAULT_MFDS, settings);
    CH_Data refCH = CH_Data.create(6.5, 0.0, 1.0, false);
    refCH.appendTo(mfdRef, null);

    for (Entry<String, List<Rupture>> entry : ruptureMap.entrySet()) {
      addComment(entry.getKey(), root);
      double mag = Double.NaN;
      for (Rupture rupture : entry.getValue()) {
        if (rupture.mag != mag) {
          mag = rupture.mag;
          addComment(String.format(" M=%s ", MathUtils.round(mag, 3)), root);
        }
        rupture.appendTo(root, refCH);
      }
    }

    TransformerFactory transFactory = TransformerFactory.newInstance();
    Transformer trans = transFactory.newTransformer();
    trans.setOutputProperty(OutputKeys.INDENT, "yes");
    trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    DOMSource source = new DOMSource(docOut);

    StreamResult result = new StreamResult(out);
    trans.transform(source, result);

  }

  static final String DISCLAIMER = " This model is an example and for review purposes only ";

  static void addDisclaimer(Element e) {
    Parsing.addComment(DISCLAIMER, e);
  }

}
