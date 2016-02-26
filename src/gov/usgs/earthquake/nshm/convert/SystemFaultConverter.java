package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.eq.model.SourceAttribute.ASEIS;
import static org.opensha2.eq.model.SourceAttribute.DEPTH;
import static org.opensha2.eq.model.SourceAttribute.DIP;
import static org.opensha2.eq.model.SourceAttribute.DIP_DIR;
import static org.opensha2.eq.model.SourceAttribute.ID;
import static org.opensha2.eq.model.SourceAttribute.INDEX;
import static org.opensha2.eq.model.SourceAttribute.INDICES;
import static org.opensha2.eq.model.SourceAttribute.LOWER_DEPTH;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RAKE;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceAttribute.WIDTH;
import static org.opensha2.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha2.eq.model.SourceElement.GEOMETRY;
import static org.opensha2.eq.model.SourceElement.SECTION;
import static org.opensha2.eq.model.SourceElement.SETTINGS;
import static org.opensha2.eq.model.SourceElement.SOURCE;
import static org.opensha2.eq.model.SourceElement.SYSTEM_FAULT_SECTIONS;
import static org.opensha2.eq.model.SourceElement.SYSTEM_SOURCE_SET;
import static org.opensha2.eq.model.SourceElement.TRACE;
import static org.opensha2.eq.model.SourceType.SYSTEM;
import static org.opensha2.util.Parsing.addAttribute;
import static org.opensha2.util.Parsing.addComment;
import static org.opensha2.util.Parsing.addElement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

import org.opensha2.eq.fault.surface.DefaultGriddedSurface;
import org.opensha2.eq.fault.surface.GriddedSurface;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.util.Parsing;
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
class SystemFaultConverter {

	static final String SECTION_XML_IN = "fault_sections.xml";
	static final String SECTION_XML_OUT = SECTION_XML_IN;
	static final String RUPTURES_XML_OUT = "fault_ruptures.xml";

	static final String RUPTURES_BIN_IN = "rup_sections.bin";
	static final String MAGS_BIN_IN = "mags.bin";
	static final String RATES_BIN_IN = "rates.bin";
	static final String RAKES_BIN_IN = "rakes.bin";

	private SystemFaultConverter() {};

	static SystemFaultConverter create() {
		SystemFaultConverter ifc = new SystemFaultConverter();
		return ifc;
	}

	void process(Path solPath, Path outDir, UC3_Filter filter)
			throws IOException, ParserConfigurationException, SAXException, TransformerException {

		String solName = solPath.getFileName().toString();
		solName = solName.substring(0, solName.lastIndexOf('.'));
		Path brAvgId = solPath.getParent().getFileName();
		Path solDir = outDir.resolve(brAvgId).resolve(SYSTEM.toString()).resolve(solName);
		Files.createDirectories(solDir);

		ZipFile zip = new ZipFile(solPath.toString());

		double weight = SystemConverter.computeWeight(solName);
		
		System.out.println("");
		System.out.println("  Solution file: " + zip.getName());
		System.out.println("         Weight: " + weight);
		
		// section XML
		File sectionsOut = solDir.resolve(SECTION_XML_OUT).toFile();
		ZipEntry sectionsEntry = zip.getEntry(SECTION_XML_IN);
		SectionData sectData = processSections(zip.getInputStream(sectionsEntry), sectionsOut,
			solName);

		// rupture XML -- the file reading below assumes files smaller
		// than 4G per... (int) magsEntry.getSize()
		ZipEntry magsEntry = zip.getEntry(MAGS_BIN_IN);
		List<Double> rupMags = Parsing.readBinaryDoubleList(zip.getInputStream(magsEntry),
			(int) magsEntry.getSize());
		System.out.println("      Mags file: " + rupMags.size());
		ZipEntry ratesEntry = zip.getEntry(RATES_BIN_IN);
		List<Double> rupRates = Parsing.readBinaryDoubleList(zip.getInputStream(ratesEntry),
			(int) ratesEntry.getSize());
		System.out.println("     Rates file: " + rupRates.size());
		ZipEntry rakesEntry = zip.getEntry(RAKES_BIN_IN);
		List<Double> rupRakes = Parsing.readBinaryDoubleList(zip.getInputStream(rakesEntry),
			(int) rakesEntry.getSize());
		System.out.println("     Rakes file: " + rupRakes.size());
		ZipEntry rupsEntry = zip.getEntry(RUPTURES_BIN_IN);
		List<List<Integer>> rupIndices = Parsing.readBinaryIntLists(zip.getInputStream(rupsEntry));
		System.out.println("   Indices file: " + rupIndices.size());
		File rupsOut = solDir.resolve(RUPTURES_XML_OUT).toFile();

		// build rupture fields that require building indexed surfaces
		System.out.println("Building averaged rupture data ...");
		sectData.buildRuptureData(rupIndices);
		List<Double> rupDips = Doubles.asList(sectData.rupDips);
		List<Double> rupDepths = Doubles.asList(sectData.rupDepths);
		List<Double> rupWidths = Doubles.asList(sectData.rupWidths);
		System.out.println(" Fault ruptures: " + rupDips.size());

		processRuptures(rupIndices, rupMags, rupRates, rupRakes, rupDips, rupWidths, rupDepths,
			solName, rupsOut, weight, filter);

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
		addAttribute(ID, -1, root);
		Converter.addDisclaimer(root);
		addComment(" Reference: " + id + " ", root);
		addComment(" Description: " + nameToDescription(id), root);

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
			double scaledRate = SystemAftershockFilter.scaleFaultRate(mag, rate);
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
		System.out.println("      Zero rate: " + zeroRate);
		System.out.println("   UC3 Filtered: " + uc3filter);
		System.out.println("  Positive rate: " + nonZeroRate);
		checkState(zeroRate + nonZeroRate + uc3filter == rupIndices.size());

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
	private SectionData processSections(InputStream in, File out, String id)
			throws ParserConfigurationException, SAXException, IOException, TransformerException {

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(in);
		docIn.getDocumentElement().normalize();

		// file out
		Document docOut = dBuilder.newDocument();
		Element root = docOut.createElement(SYSTEM_FAULT_SECTIONS.toString());
		addAttribute(NAME, id, root);
		Converter.addDisclaimer(root);
		addComment(" Reference: " + id + " ", root);

		docOut.appendChild(root);

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
			Element sectOut = addElement(SECTION, root);
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
			addAttribute(DEPTH, depth, "%.5f", geomOut);
			data.depths.add(depth);

			double lowerDepth = Double.valueOf(sectIn.getAttribute("aveLowerDepth"));
			addAttribute(LOWER_DEPTH, lowerDepth, "%.5f", geomOut);
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

			// System.out.println("        Section: [" + sectIdx + "] " +
			// sectName);
		}

		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);

		System.out.println(" Fault sections: " + data.traces.size());

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

		void buildRuptureData(List<List<Integer>> indicesList) {

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
				SystemFaultSurface ifs = createIndexedSurface(sections, indices);
				rupDepths[count] = ifs.rupDepth();
				rupWidths[count] = ifs.rupWidth();
				rupDips[count] = ifs.rupDip();
				if (count % 50000 == 0) {
					System.out.println("      completed: " + count);
				}
				count++;
			}
		}
	}

	private static SystemFaultSurface createIndexedSurface(List<GriddedSurface> sections,
			List<Integer> indices) {

		List<GriddedSurface> rupSections = Lists.newArrayList();
		for (int i : indices) {
			rupSections.add(sections.get(i));
		}
		return new SystemFaultSurface(rupSections);
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
	
	static String nameToDescription(String name) {
		StringBuilder sb = new StringBuilder();
		if (name.contains("UC33")) sb.append("UCERF 3.3 ");
		if (name.contains("brAvg")) sb.append("Branch Averaged Solution (");
		if (name.contains("FM31")) sb.append("FM31");
		if (name.contains("FM32")) sb.append("FM32");
		sb.append(")");
		return sb.toString();
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

}
