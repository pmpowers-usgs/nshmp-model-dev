package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opensha.eq.forecast.SourceElement.*;
import static org.opensha.util.Parsing.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class IndexedFaultConverter {

	private static final String SOL_PATH = "/Users/pmpowers/projects/svn/OpenSHA/tmp/tmpsol/";
	private static final String SOL_NAME = "UC33brAvg_FM31_ABM_ELLB";
	
	private static final String SECTION_XML_IN = "fault_sections.xml";
	private static final String SECTION_XML_OUT = "sections.xml";
	
	private static final String RUPTURES_BIN_IN = "rup_sections.bin";
	private static final String MAGS_BIN_IN = "mags.bin";
	private static final String RATES_BIN_IN = "rates.bin";
	private static final String RAKES_BIN_IN = "rakes.bin";
	private static final String RUPTURES_XML_OUT = "ruptures.xml";

	public static void main(String[] args) throws Exception {
		processSolution(SOL_PATH, SOL_NAME);
	}
	
	private static void processSolution(String dir, String sol)
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException {
		
		ZipFile zip = new ZipFile(dir + sol + ".zip");
		File outDir = new File(dir + sol);
		outDir.mkdir();
		
		// rupture XML -- the file reading below assumes files smaller 
		// than 4G per... (int) magsEntry.getSize()
		ZipEntry magsEntry = zip.getEntry(MAGS_BIN_IN);
		List<Double> mags = Parsing.readBinaryDoubleList(
			zip.getInputStream(magsEntry), (int) magsEntry.getSize());
		ZipEntry ratesEntry = zip.getEntry(RATES_BIN_IN);
		List<Double> rates = Parsing.readBinaryDoubleList(
			zip.getInputStream(ratesEntry), (int) ratesEntry.getSize());
		ZipEntry rakesEntry = zip.getEntry(RAKES_BIN_IN);
		List<Double> rakes = Parsing.readBinaryDoubleList(
			zip.getInputStream(rakesEntry), (int) rakesEntry.getSize());
		ZipEntry rupsEntry = zip.getEntry(RUPTURES_BIN_IN);
		List<List<Integer>> rupIndices = Parsing.readBinaryIntLists(zip
			.getInputStream(rupsEntry));
		File rupsOut = new File(outDir, RUPTURES_XML_OUT);
		processRuptures(rupIndices, mags, rates, rakes, sol, rupsOut);

		// section XML
		File sectionsOut = new File(outDir, SECTION_XML_OUT);
		ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);
		processSections(zip.getInputStream(sectionsEntry), sectionsOut, sol);
		
		zip.close();
	}
	
	/*
	 * Consolidates rupture indices, mag, rate, and rake data into single XML
	 * file.
	 */
	private static void processRuptures(List<List<Integer>> rupIndices,
			List<Double> mags, List<Double> rates, List<Double> rakes,
			String id, File out) throws ParserConfigurationException, TransformerException {
		
		checkArgument(rupIndices.size() == mags.size(), "size mismatch [%d, %d]", rupIndices.size(), mags.size());
		checkArgument(rupIndices.size() == rates.size(), "size mismatch [%d, %d]", rupIndices.size(), rates.size());
		checkArgument(rupIndices.size() == rakes.size(), "size mismatch [%d, %d]", rupIndices.size(), rakes.size());
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.newDocument();
        Element root = doc.createElement(INDEXED_FAULT_SOURCE_SET.toString());
        root.setAttribute("id", id);
        doc.appendChild(root);
        
        // TODO add aleatory <MagUncertainty>
        
        for (int i=0; i<rupIndices.size(); i++) {
        	Element sourceElem = addElement(FAULT_SOURCE, root);
        	CH_Data mfdData = CH_Data.create(mags.get(i), rates.get(i), 1.0);
        	mfdData.appendTo(sourceElem);
        	Element geom = addElement(GEOMETRY, sourceElem);
        	geom.setAttribute("rake", String.format("%.1f", rakes.get(i)));
			geom.setAttribute("indices",
				Parsing.intListToRangeString(rupIndices.get(i)));
        }
        
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
	}
	
	/*
	 * Simplifies fault system solution section XML - preserves dip dir from
	 * splitting of parent faults
	 */
	private static void processSections(InputStream in, File out, String id)
			throws ParserConfigurationException, SAXException, IOException,
			TransformerException {
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(in);
		docIn.getDocumentElement().normalize();
		
        // file out
        Document docOut = dBuilder.newDocument();
        Element rootOut = docOut.createElement(INDEXED_FAULT_SECTIONS.toString());
        rootOut.setAttribute("id", id);
        docOut.appendChild(rootOut);
        
        // file in
        Element rootIn = docIn.getDocumentElement();
		NodeList sectsIn = ((Element) rootIn.getElementsByTagName(
			"FaultSectionPrefDataList").item(0)).getChildNodes();
        for (int i=0; i<sectsIn.getLength(); i++) {
        	Node node = sectsIn.item(i);
        	if (!(node instanceof Element)) continue;
        	Element sectIn = (Element) node;
			LocationList trace = readTrace((Element) sectIn
				.getElementsByTagName("FaultTrace").item(0));

			// add to out
        	Element sectOut = addElement(FAULT_SECTION, rootOut);
        	sectOut.setAttribute("name", cleanName(sectIn.getAttribute("sectionName")));
        	sectOut.setAttribute("index", sectIn.getAttribute("sectionId"));
        	Element geomOut = addElement(GEOMETRY, sectOut);
        	// TODO rem reformat (below) uses stripZeros
        	geomOut.setAttribute("dip", Parsing.reformat(sectIn.getAttribute("aveDip"), "%.1f"));
        	geomOut.setAttribute("dipDir", Parsing.reformat(sectIn.getAttribute("dipDirection"), "%.3f"));
        	geomOut.setAttribute("upperDepth", Parsing.reformat(sectIn.getAttribute("aveUpperDepth"), Location.FORMAT));
        	geomOut.setAttribute("lowerDepth", Parsing.reformat(sectIn.getAttribute("aveLowerDepth"), Location.FORMAT));
        	// Section rakes are of no consequence as rupture rakes are read 
        	// from a file. These are presumably correctly area-weight averaged
        	// as dips are when building ruptures and (TODO) in the future 
        	// should probably be computed the same way -- leaving consistent 
        	// with OpenSHA implementation for now
        	// geomOut.setAttribute("rake", Parsing.reformat(sectIn.getAttribute("aveRake"), "%.1f"));
        	geomOut.setAttribute("aseis", Parsing.reformat(sectIn.getAttribute("aseismicSlipFactor"), "%.4f"));
        	Element traceOut = addElement(TRACE, geomOut);
        	traceOut.setTextContent(trace.toString());

        }

		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
	}

	private static LocationList readTrace(Element trace) {
		NodeList locNodes = trace.getElementsByTagName("Location");
		LocationList.Builder locs = LocationList.builder();
		for (int i=0; i<locNodes.getLength(); i++) {
			Element locElem = (Element) locNodes.item(i);
			locs.add(Location.create(
				Double.valueOf(locElem.getAttribute("Latitude")),
				Double.valueOf(locElem.getAttribute("Longitude")),
				Double.valueOf(locElem.getAttribute("Depth"))));
		}
		return locs.build();
	}
	
	// replaces ', Subsection' with ':'
	private static String cleanName(String name) {
		return name.replace(", Subsection", " :");
	}
	

}
