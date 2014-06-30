package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static org.opensha.eq.forecast.SourceAttribute.*;
import static org.opensha.eq.forecast.SourceElement.*;
import static org.opensha.util.Parsing.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
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

import org.opensha.eq.fault.surface.GriddedSurfaceWithSubsets;
import org.opensha.eq.fault.surface.RuptureSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
class IndexedFaultConverter {

	static final String SECTION_XML_IN = "fault_sections.xml";
	static final String SECTION_XML_OUT = "sections.xml";

	static final String RUPTURES_BIN_IN = "rup_sections.bin";
	static final String MAGS_BIN_IN = "mags.bin";
	static final String RATES_BIN_IN = "rates.bin";
	static final String RAKES_BIN_IN = "rakes.bin";
	static final String RUPTURES_XML_OUT = "ruptures.xml";

	// @formatter:off
	static void processSolution(String solDirPath, String sol, String outDirPath, double weight)
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException {
		
		ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
		File outDir = new File(outDirPath + sol);
		outDir.mkdirs();
		
		// rupture XML -- the file reading below assumes files smaller 
		// than 4G per... (int) magsEntry.getSize()
		ZipEntry magsEntry = zip.getEntry(MAGS_BIN_IN);
		List<Double> mags = Parsing.readBinaryDoubleList(zip.getInputStream(magsEntry), (int) magsEntry.getSize());
		ZipEntry ratesEntry = zip.getEntry(RATES_BIN_IN);
		List<Double> rates = Parsing.readBinaryDoubleList(zip.getInputStream(ratesEntry), (int) ratesEntry.getSize());
		ZipEntry rakesEntry = zip.getEntry(RAKES_BIN_IN);
		List<Double> rakes = Parsing.readBinaryDoubleList(zip.getInputStream(rakesEntry), (int) rakesEntry.getSize());
		ZipEntry rupsEntry = zip.getEntry(RUPTURES_BIN_IN);
		List<List<Integer>> rupIndices = Parsing.readBinaryIntLists(zip.getInputStream(rupsEntry));
		File rupsOut = new File(outDir, RUPTURES_XML_OUT);
		
		
		// TODO need to build these for realz 
		List<Double> dips = createFilledList(rupIndices.size());
		List<Double> widths = createFilledList(rupIndices.size());
		List<Double> depths = createFilledList(rupIndices.size());
		
		
		processRuptures(rupIndices, mags, rates, rakes, dips, widths, depths, sol, rupsOut, weight);
		
		// section XML
		File sectionsOut = new File(outDir, SECTION_XML_OUT);
		ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);
		processSections(zip.getInputStream(sectionsEntry), sectionsOut, sol);
		
		zip.close();
	}
	
	private static List<Double> createFilledList(int size) {
		List<Double> list = Lists.newArrayListWithCapacity(size);
		for (int i=0; i<size; i++) {
			list.add(Double.NaN);
		}
		return list;		
	}
	
	/*
	 * Consolidates rupture indices, mag, rate, and rake data into single XML
	 * file.
	 */
	private static void processRuptures(List<List<Integer>> rupIndices,
			List<Double> mags, List<Double> rates, List<Double> rakes, List<Double> dips,
			List<Double> widths, List<Double> depths, String id, File out, double weight)
					throws ParserConfigurationException, TransformerException {
		
		int rupSize = rupIndices.size();
		checkSize(depths, rupSize, "depths");
		checkSize(dips, rupSize, "dips");
		checkSize(mags, rupSize, "mags");
		checkSize(rakes, rupSize, "rakes");
		checkSize(rates, rupSize, "rates");
		checkSize(widths, rupSize, "widths");
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.newDocument();
        Element root = doc.createElement(INDEXED_FAULT_SOURCE_SET.toString());
        doc.appendChild(root);
        addAttribute(NAME, id, root);
		addAttribute(WEIGHT, weight, root);
        
        // settings and defaults
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(MAG_FREQ_DIST_REF, settings);
		CH_Data refCH = CH_Data.create(6.5, 0.0, 1.0, false);
		refCH.appendTo(mfdRef, null);
        
        for (int i=0; i<rupIndices.size(); i++) {
        	Element sourceElem = addElement(SOURCE, root);
        	CH_Data mfdData = CH_Data.create(mags.get(i), rates.get(i), 1.0, false);
        	mfdData.appendTo(sourceElem, refCH);
        	Element geom = addElement(GEOMETRY, sourceElem);

        	addAttribute(DIP, dips.get(i), "%.1f", geom);
        	addAttribute(INDICES, Parsing.intListToRangeString(rupIndices.get(i)), geom);
        	addAttribute(WIDTH, widths.get(i), "%.3f", geom);
        	addAttribute(DEPTH, depths.get(i), "%.3f", geom);
        	addAttribute(RAKE, rakes.get(i), "%.1f", geom);
        }
        
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
	}
	
	private static void checkSize(List<Double> data, int target, String id) {
		checkArgument(data.size() == target, "%s size mismatch [%s, %s]", id, target, data.size());
	}
	
	/*
	 * Simplifies fault system solution section XML - preserves dip dir from
	 * splitting of parent faults
	 */
	private static SectionData processSections(InputStream in, File out, String id)
			throws ParserConfigurationException, SAXException, IOException,
			TransformerException {
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(in);
		docIn.getDocumentElement().normalize();
		
        // file out
        Document docOut = dBuilder.newDocument();
        Element rootOut = docOut.createElement(INDEXED_FAULT_SECTIONS.toString());
        addAttribute(NAME, id, rootOut);
        docOut.appendChild(rootOut);
        
        // file in
        Element rootIn = docIn.getDocumentElement();
		NodeList sectsIn = ((Element) rootIn.getElementsByTagName(
			"FaultSectionPrefDataList").item(0)).getChildNodes();
		
		// init data collectors
		SectionData data = new SectionData(sectsIn.getLength());
		
        for (int i=0; i<sectsIn.getLength(); i++) {
        	Node node = sectsIn.item(i);
        	if (!(node instanceof Element)) continue;
        	Element sectIn = (Element) node;
			LocationList trace = readTrace((Element) sectIn
				.getElementsByTagName("FaultTrace").item(0));

			// add to out
        	Element sectOut = addElement(SECTION, rootOut);
        	addAttribute(NAME, cleanName(sectIn.getAttribute("sectionName")), sectOut);
        	addAttribute(INDEX, sectIn.getAttribute("sectionId"), sectOut);
        	Element geomOut = addElement(GEOMETRY, sectOut);
        	
        	double dip = Double.valueOf(sectIn.getAttribute("aveDip"));
        	addAttribute(DIP, dip, "%.1f", geomOut);
        	data.dips.add(dip);
        	
        	double dipDir = Double.valueOf(sectIn.getAttribute("dipDirection"));
        	addAttribute(DIP_DIR, dipDir, "%.3f", geomOut);
        	data.dipDirs.add(dipDir);
        	
        	double depth = Double.valueOf(sectIn.getAttribute("aveUpperDepth"));
        	addAttribute(DEPTH, depth, Location.FORMAT, geomOut);
        	data.depths.add(depth);
        	
        	double lowerDepth = Double.valueOf(sectIn.getAttribute("aveLowerDepth"));
        	addAttribute(LOWER_DEPTH, lowerDepth, Location.FORMAT, geomOut);
        	data.lowerDepths.add(lowerDepth);
        	
        	double aseis = Double.valueOf(sectIn.getAttribute("aseismicSlipFactor"));
        	addAttribute(ASEIS, aseis, "%.4f", geomOut);
        	data.aseises.add(aseis);

        	// Section rakes are of no consequence as rupture rakes are read 
        	// from a file. These are presumably correctly area-weight averaged
        	// as dips are when building ruptures and (TODO) in the future 
        	// should probably be computed the same way -- leaving consistent 
        	// with OpenSHA implementation for now
        	
        	Element traceOut = addElement(TRACE, geomOut);
        	traceOut.setTextContent(trace.toString());
        	data.traces.add(trace);

        }

		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
		
		return data;
	}
	
	static class SectionData {
		
		final List<Double> dips;
		final List<Double> dipDirs;
		final List<Double> depths;
		final List<Double> lowerDepths;
		final List<Double> aseises;
		final List<LocationList> traces;

		SectionData(int size) {
			dips = Lists.newArrayListWithCapacity(size);
			dipDirs = Lists.newArrayListWithCapacity(size);
			depths = Lists.newArrayListWithCapacity(size);
			lowerDepths = Lists.newArrayListWithCapacity(size);
			aseises = Lists.newArrayListWithCapacity(size);
			traces = Lists.newArrayListWithCapacity(size);
		}
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
	static String cleanName(String name) {
		return name.replace(", Subsection", " :");
	}
	
	// @formatter:on
	
	
	// compute lists of dip, width, and depth
	// identifiers
	enum SectionDataType { DIPS, DIP_DIRS, DEPTHS, LOWER_DEPTHS, ASEISES }
	
	
	// need list of sections
	//  and list of section indices for each rupture
	
//	static List<RuptureSurface> createSectionSurfaces() {
//		
//		
//	}
	
	
////	from FaultSectionPrefData
//	public synchronized GriddedSurfaceWithSubsets getSectionSurface(
//			double gridSpacing, boolean preserveGridSpacingExactly,
//			boolean aseisReducesArea) {
//
//		// need to get the aseis reduced area section attributes
//		LocationList trace;
//		double dip;
//		double depth;
//		double width; // --> compute bottom
//		double bottom;
//		double spacing;
//		
//		
//		return new GriddedSurfaceWithSubsets(trace, dip, depth, bottom, spacing, spacing);
//	}

	
	
//	static Map<DataType, List<Double>> computeDipsWidthsAndDepths(
//		List<? extends RuptureSurface> sections,
//		List<List<Integer>> rupIndices) {
//		
//		
//		
//		
//		return null;
//	}
	
	// TODO
//	static void combineSolution(String solDirPath, String sol1, String sol2, String outDirPath)
//			throws IOException, ParserConfigurationException, SAXException,
//			TransformerException {
//		
//		ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
//		File outDir = new File(outDirPath + sol);
//		outDir.mkdirs();
//		
//		// rupture XML -- the file reading below assumes files smaller 
//		// than 4G per... (int) magsEntry.getSize()
//		ZipEntry magsEntry = zip.getEntry(MAGS_BIN_IN);
//		List<Double> mags = Parsing.readBinaryDoubleList(zip.getInputStream(magsEntry), (int) magsEntry.getSize());
//		ZipEntry ratesEntry = zip.getEntry(RATES_BIN_IN);
//		List<Double> rates = Parsing.readBinaryDoubleList(zip.getInputStream(ratesEntry), (int) ratesEntry.getSize());
//		ZipEntry rakesEntry = zip.getEntry(RAKES_BIN_IN);
//		List<Double> rakes = Parsing.readBinaryDoubleList(zip.getInputStream(rakesEntry), (int) rakesEntry.getSize());
//		ZipEntry rupsEntry = zip.getEntry(RUPTURES_BIN_IN);
//		List<List<Integer>> rupIndices = Parsing.readBinaryIntLists(zip.getInputStream(rupsEntry));
//		File rupsOut = new File(outDir, RUPTURES_XML_OUT);
//		processRuptures(rupIndices, mags, rates, rakes, sol, rupsOut);
//
//		// section XML
//		File sectionsOut = new File(outDir, SECTION_XML_OUT);
//		ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);
//		processSections(zip.getInputStream(sectionsEntry), sectionsOut, sol);
//		
//		zip.close();
//	}


}
