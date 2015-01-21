package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.model.SourceAttribute.ASEIS;
import static org.opensha.eq.model.SourceAttribute.DEPTH;
import static org.opensha.eq.model.SourceAttribute.DIP;
import static org.opensha.eq.model.SourceAttribute.DIP_DIR;
import static org.opensha.eq.model.SourceAttribute.INDEX;
import static org.opensha.eq.model.SourceAttribute.INDICES;
import static org.opensha.eq.model.SourceAttribute.LOWER_DEPTH;
import static org.opensha.eq.model.SourceAttribute.NAME;
import static org.opensha.eq.model.SourceAttribute.RAKE;
import static org.opensha.eq.model.SourceAttribute.WEIGHT;
import static org.opensha.eq.model.SourceAttribute.WIDTH;
import static org.opensha.eq.model.SourceElement.GEOMETRY;
import static org.opensha.eq.model.SourceElement.SYSTEM_FAULT_SECTIONS;
import static org.opensha.eq.model.SourceElement.SYSTEM_SOURCE_SET;
import static org.opensha.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha.eq.model.SourceElement.SECTION;
import static org.opensha.eq.model.SourceElement.SETTINGS;
import static org.opensha.eq.model.SourceElement.SOURCE;
import static org.opensha.eq.model.SourceElement.TRACE;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;
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

import org.opensha.eq.fault.surface.GriddedSurface;
import org.opensha.eq.fault.surface.DefaultGriddedSurface;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
class IndexedFaultConverter {

	static final String SECTION_XML_IN = "fault_sections.xml";
	static final String SECTION_XML_OUT = SECTION_XML_IN;
	static final String RUPTURES_XML_OUT = "fault_ruptures.xml";

	static final String RUPTURES_BIN_IN = "rup_sections.bin";
	static final String MAGS_BIN_IN = "mags.bin";
	static final String RATES_BIN_IN = "rates.bin";
	static final String RAKES_BIN_IN = "rakes.bin";

	private Logger log;

	private IndexedFaultConverter() {};

	static IndexedFaultConverter create(Logger log) {
		IndexedFaultConverter ifc = new IndexedFaultConverter();
		ifc.log = checkNotNull(log);
		return ifc;
	}

	void processSolution(String solDirPath, String sol, String outDirPath, double weight, UC3_Filter filter)
			throws IOException, ParserConfigurationException, SAXException, TransformerException {

		ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
		File outDir = new File(outDirPath + sol);
		outDir.mkdirs();

		log.info("");
		log.info("  Solution file: " + zip.getName());

		// section XML
		File sectionsOut = new File(outDir, SECTION_XML_OUT);
		ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);
		SectionData sectData = processSections(zip.getInputStream(sectionsEntry), sectionsOut, sol);

		// rupture XML -- the file reading below assumes files smaller
		// than 4G per... (int) magsEntry.getSize()
		ZipEntry magsEntry = zip.getEntry(MAGS_BIN_IN);
		List<Double> rupMags = Parsing.readBinaryDoubleList(zip.getInputStream(magsEntry),
			(int) magsEntry.getSize());
		log.info("      Mags file: " + rupMags.size());
		ZipEntry ratesEntry = zip.getEntry(RATES_BIN_IN);
		List<Double> rupRates = Parsing.readBinaryDoubleList(zip.getInputStream(ratesEntry),
			(int) ratesEntry.getSize());
		log.info("     Rates file: " + rupRates.size());
		ZipEntry rakesEntry = zip.getEntry(RAKES_BIN_IN);
		List<Double> rupRakes = Parsing.readBinaryDoubleList(zip.getInputStream(rakesEntry),
			(int) rakesEntry.getSize());
		log.info("     Rakes file: " + rupRakes.size());
		ZipEntry rupsEntry = zip.getEntry(RUPTURES_BIN_IN);
		List<List<Integer>> rupIndices = Parsing.readBinaryIntLists(zip.getInputStream(rupsEntry));
		log.info("   Indices file: " + rupIndices.size());
		File rupsOut = new File(outDir, RUPTURES_XML_OUT);

		// build rupture fields that require building indexed surfaces
		log.info("Building averaged rupture data ...");
		sectData.buildRuptureData(rupIndices, log);
		List<Double> rupDips = Doubles.asList(sectData.rupDips);
		List<Double> rupDepths = Doubles.asList(sectData.rupDepths);
		List<Double> rupWidths = Doubles.asList(sectData.rupWidths);
		log.info(" Fault ruptures: " + rupDips.size());

		processRuptures(rupIndices, rupMags, rupRates, rupRakes, rupDips, rupWidths, rupDepths,
			sol, rupsOut, weight, filter);

		zip.close();
	}

	/*
	 * Consolidates rupture indices, mag, rate, and rake data into single XML
	 * file. Filters out ruptures with rate = 0.
	 */
	private void processRuptures(List<List<Integer>> rupIndices, List<Double> mags,
			List<Double> rates, List<Double> rakes, List<Double> dips, List<Double> widths,
			List<Double> depths, String id, File out, double weight, UC3_Filter filter)
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
		Element root = doc.createElement(SYSTEM_SOURCE_SET.toString());
		doc.appendChild(root);
		addAttribute(NAME, id, root);
		addAttribute(WEIGHT, weight, root);

		// settings and defaults
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		CH_Data refCH = CH_Data.create(6.5, 0.0, 1.0, false);
		refCH.appendTo(mfdRef, null);

		int zeroRate = 0;
		int nonZeroRate = 0;
		int uc3filter = 0;
		for (int i = 0; i < rupIndices.size(); i++) {
			double rate = rates.get(i);
			if (rate == 0.0) {
				zeroRate++;
				continue;
			}
			if (filter.filter(rupIndices.get(i))) {
				uc3filter++;
				continue;
			}
			Element sourceElem = addElement(SOURCE, root);
			double mag = mags.get(i);
			double scaledRate = IndexedAftershockFilter.scaleFaultRate(mag, rate);
			CH_Data mfdData = CH_Data.create(mag, scaledRate, 1.0, false);
			mfdData.appendTo(sourceElem, refCH);
			Element geom = addElement(GEOMETRY, sourceElem);

			addAttribute(DIP, dips.get(i), "%.1f", geom);
			addAttribute(INDICES, Parsing.intListToRangeString(rupIndices.get(i)), geom);
			addAttribute(WIDTH, widths.get(i), "%.3f", geom);
			addAttribute(DEPTH, depths.get(i), "%.3f", geom);
			addAttribute(RAKE, rakes.get(i), "%.1f", geom);
			nonZeroRate++;
		}
		log.info("      Zero rate: " + zeroRate);
		log.info("   UC3 Filtered: " + uc3filter);
		log.info("  Positive rate: " + nonZeroRate);
		checkState(zeroRate + nonZeroRate + uc3filter == rupIndices.size());

		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);

		log.info("Conversion complete");
		log.info("");

	}

	private static void checkSize(List<Double> data, int target, String id) {
		checkArgument(data.size() == target, "%s size mismatch [%s, %s]", id, target, data.size());
	}

	/*
	 * Simplifies fault system solution section XML - preserves dip dir from
	 * splitting of parent faults
	 */
	private SectionData processSections(InputStream in, File out, String id)
			throws ParserConfigurationException, SAXException, IOException, TransformerException {

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(in);
		docIn.getDocumentElement().normalize();

		// file out
		Document docOut = dBuilder.newDocument();
		Element rootOut = docOut.createElement(SYSTEM_FAULT_SECTIONS.toString());
		addAttribute(NAME, id, rootOut);
		docOut.appendChild(rootOut);

		// file in
		Element rootIn = docIn.getDocumentElement();
		NodeList sectsIn = ((Element) rootIn.getElementsByTagName("FaultSectionPrefDataList").item(
			0)).getChildNodes();

		// init data collectors
		SectionData data = new SectionData(sectsIn.getLength());

		for (int i = 0; i < sectsIn.getLength(); i++) {
			Node node = sectsIn.item(i);
			if (!(node instanceof Element)) continue;
			Element sectIn = (Element) node;
			LocationList trace = readTrace((Element) sectIn.getElementsByTagName("FaultTrace")
				.item(0));

			// add to out
			Element sectOut = addElement(SECTION, rootOut);
			String sectName = sectIn.getAttribute("sectionName");
			addAttribute(NAME, sectName, sectOut);
			String sectIdx = sectIn.getAttribute("sectionId");
			addAttribute(INDEX, sectIdx, sectOut);
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

			log.fine("        Section: [" + sectIdx + "] " + sectName);
		}

		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);

		log.info(" Fault sections: " + data.traces.size());

		return data;
	}

	/*
	 * Wrapper class for section data. Class is used to build section surfaces
	 * and then build multi-section surfaces for each rupture to derive
	 * (area-weight-averaged) rupture depths, widths, and dips.
	 */
	static class SectionData {

		final List<Double> dips;
		final List<Double> dipDirs;
		final List<Double> depths;
		final List<Double> lowerDepths;
		final List<Double> aseises;
		final List<LocationList> traces;

		// rupture data lists
		double[] rupDepths;
		double[] rupWidths;
		double[] rupDips;

		SectionData(int size) {
			dips = Lists.newArrayListWithCapacity(size);
			dipDirs = Lists.newArrayListWithCapacity(size);
			depths = Lists.newArrayListWithCapacity(size);
			lowerDepths = Lists.newArrayListWithCapacity(size);
			aseises = Lists.newArrayListWithCapacity(size);
			traces = Lists.newArrayListWithCapacity(size);
		}

		void buildRuptureData(List<List<Integer>> indicesList, Logger log) {

			// create sesction list
			List<GriddedSurface> sections = Lists.newArrayList();
			for (int i = 0; i < traces.size(); i++) {
				// corrected depth
				double depth = depths.get(i) +
					(aseises.get(i) * (lowerDepths.get(i) - depths.get(i)));
				DefaultGriddedSurface surface = DefaultGriddedSurface.builder()
					.trace(traces.get(i)).depth(depth).dip(dips.get(i)).dipDir(dipDirs.get(i))
					.lowerDepth(lowerDepths.get(i)).build();
				sections.add(surface);
			}

			// create IndexedFaultSurfaces from section lists

			int size = indicesList.size();
			rupDepths = new double[size];
			rupWidths = new double[size];
			rupDips = new double[size];

			int count = 0;
			for (List<Integer> indices : indicesList) {
				IndexedFaultSurface ifs = createIndexedSurface(sections, indices);
				rupDepths[count] = ifs.rupDepth();
				rupWidths[count] = ifs.rupWidth();
				rupDips[count] = ifs.rupDip();
				if (count % 50000 == 0) {
					log.info("      completed: " + count);
				}
				count++;
			}
		}
	}

	private static IndexedFaultSurface createIndexedSurface(List<GriddedSurface> sections,
			List<Integer> indices) {

		List<GriddedSurface> rupSections = Lists.newArrayList();
		for (int i : indices) {
			rupSections.add(sections.get(i));
		}
		return new IndexedFaultSurface(rupSections);
	}

	private static LocationList readTrace(Element trace) {
		NodeList locNodes = trace.getElementsByTagName("Location");
		LocationList.Builder locs = LocationList.builder();
		for (int i = 0; i < locNodes.getLength(); i++) {
			Element locElem = (Element) locNodes.item(i);
			locs.add(Location.create(Double.valueOf(locElem.getAttribute("Latitude")),
				Double.valueOf(locElem.getAttribute("Longitude")),
				Double.valueOf(locElem.getAttribute("Depth"))));
		}
		return locs.build();
	}

	// replaces ', Subsection' with ':'
	static String cleanName(String name) {
		return name.replace(", Subsection", " :");
	}
	
	
	/*
	 * Filter enum for Klamath and Carson rupture filters. Carson ruptures are
	 * restricted to the Carson-Kings (Genoa) parent fault. Klamath ruptures
	 * extend onto Klamath Lake West. In both cases though, we can just test if
	 * the Range of interest contains the first index supplied to the filter;
	 * Karson east participation always comes first in rupture indices.
	 * 
	 * This filtering makes no accomodation for removing (now unused) fault
	 * sections.
	 */
	
	static final int KLAMATH_MIN_FM31 = 2422;
	static final int KLAMATH_MAX_FM31 = 2430;
	static final int KLAMATH_MIN_FM32 = 2487;
	static final int KLAMATH_MAX_FM32 = 2495;
	
	static final int CARSON_MIN_FM31 = 257;
	static final int CARSON_MAX_FM31 = 263;
	static final int CARSON_MIN_FM32 = 261;
	static final int CARSON_MAX_FM32 = 267;

	static enum UC3_Filter {
		
		FM31(CARSON_MIN_FM31, CARSON_MAX_FM31, KLAMATH_MIN_FM31, KLAMATH_MAX_FM31),
		FM32(CARSON_MIN_FM32, CARSON_MAX_FM32, KLAMATH_MIN_FM32, KLAMATH_MAX_FM32);
		
		private Range<Integer> carsonRange;
		private Range<Integer> klamathRange;
		
		private UC3_Filter(int carsonMin, int carsonMax, int klamathMin, int klamathMax) {
			carsonRange = Range.closed(carsonMin, carsonMax);
			klamathRange = Range.closed(klamathMin, klamathMax);
		}
		
		public boolean filter(List<Integer> indices) {
			int index = indices.get(0);
			return carsonRange.contains(index) || klamathRange.contains(index);
		};
		
	}
	
	public static void main(String[] args) {
		
	}
	
	// TODO clean
//	static final int CARSON_FM31_MIN = 
//	static boolean filterForNSHMP() {
//		
//	}

	// @formatter:on

	// compute lists of dip, width, and depth
	// identifiers
	// enum SectionDataType { DIPS, DIP_DIRS, DEPTHS, LOWER_DEPTHS, ASEISES }

	// need list of sections
	// and list of section indices for each rupture

	// static List<RuptureSurface> createSectionSurfaces() {
	//
	//
	// }

	// // from FaultSectionPrefData
	// public synchronized DefaultGriddedSurface getSectionSurface(
	// double gridSpacing, boolean preserveGridSpacingExactly,
	// boolean aseisReducesArea) {
	//
	// // need to get the aseis reduced area section attributes
	// LocationList trace;
	// double dip;
	// double depth;
	// double width; // --> compute bottom
	// double bottom;
	// double spacing;
	//
	//
	// return new DefaultGriddedSurface(trace, dip, depth, bottom, spacing,
	// spacing);
	// }

	// TODO
	// static void combineSolution(String solDirPath, String sol1, String sol2,
	// String outDirPath)
	// throws IOException, ParserConfigurationException, SAXException,
	// TransformerException {
	//
	// ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
	// File outDir = new File(outDirPath + sol);
	// outDir.mkdirs();
	//
	// // rupture XML -- the file reading below assumes files smaller
	// // than 4G per... (int) magsEntry.getSize()
	// ZipEntry magsEntry = zip.getEntry(MAGS_BIN_IN);
	// List<Double> mags =
	// Parsing.readBinaryDoubleList(zip.getInputStream(magsEntry), (int)
	// magsEntry.getSize());
	// ZipEntry ratesEntry = zip.getEntry(RATES_BIN_IN);
	// List<Double> rates =
	// Parsing.readBinaryDoubleList(zip.getInputStream(ratesEntry), (int)
	// ratesEntry.getSize());
	// ZipEntry rakesEntry = zip.getEntry(RAKES_BIN_IN);
	// List<Double> rakes =
	// Parsing.readBinaryDoubleList(zip.getInputStream(rakesEntry), (int)
	// rakesEntry.getSize());
	// ZipEntry rupsEntry = zip.getEntry(RUPTURES_BIN_IN);
	// List<List<Integer>> rupIndices =
	// Parsing.readBinaryIntLists(zip.getInputStream(rupsEntry));
	// File rupsOut = new File(outDir, RUPTURES_XML_OUT);
	// processRuptures(rupIndices, mags, rates, rakes, sol, rupsOut);
	//
	// // section XML
	// File sectionsOut = new File(outDir, SECTION_XML_OUT);
	// ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);
	// processSections(zip.getInputStream(sectionsEntry), sectionsOut, sol);
	//
	// zip.close();
	// }

}
