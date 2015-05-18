package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.opensha2.eq.fault.FocalMech.NORMAL;
import static org.opensha2.eq.fault.FocalMech.REVERSE;
import static org.opensha2.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_POINT_WC94_LENGTH;
import static org.opensha2.eq.model.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha2.eq.model.SourceAttribute.MAGS;
import static org.opensha2.eq.model.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha2.eq.model.SourceAttribute.MAX_DEPTH;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RATES;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.eq.model.SourceAttribute.STRIKE;
import static org.opensha2.eq.model.SourceAttribute.TYPE;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha2.eq.model.SourceElement.GRID_SOURCE_SET;
import static org.opensha2.eq.model.SourceElement.INCREMENTAL_MFD;
import static org.opensha2.eq.model.SourceElement.NODE;
import static org.opensha2.eq.model.SourceElement.NODES;
import static org.opensha2.eq.model.SourceElement.SETTINGS;
import static org.opensha2.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha2.eq.model.SourceType.SYSTEM;
import static org.opensha2.mfd.MfdType.INCR;
import static org.opensha2.util.Parsing.addAttribute;
import static org.opensha2.util.Parsing.addElement;
import static org.opensha2.util.Parsing.enumValueMapToString;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha2.data.DataUtils;
import org.opensha2.eq.fault.FocalMech;
import org.opensha2.geo.BorderType;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.geo.Region;
import org.opensha2.geo.Regions;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 * 
 * @author Peter Powers
 */
public class SystemGridConverter {

	private static final String GRID_XML = "grid_sources.xml";
	private static final String GRID_BIN = "grid_sources.bin";

	private static final Region CA_REGION;

	// Using strings for grid mag MFD parsing because of printed rounding errors
	// in source files
	private static final double[] mags;
	private static final Set<String> magStrSet;
	private static double[] fracStrikeSlip;
	private static double[] fracNormal;
	private static double[] fracReverse;
	private static Map<FocalMech, Double> defaultMechMap;

	static {
		mags = DataUtils.buildSequence(5.05, 7.85, 0.1, true);
		magStrSet = ImmutableSet.copyOf(FluentIterable.from(Doubles.asList(mags))
			.transform(Parsing.formatDoubleFunction("%.2f")).toList());
		try {
			fracStrikeSlip = readFocalMechs("StrikeSlipWts.txt");
			fracNormal = readFocalMechs("NormalWts.txt");
			fracReverse = readFocalMechs("ReverseWts.txt");
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		// this is unused but added to <SourceProperties> for consistency
		// with other grid sources that require it; mechs can be overridden
		// in each <Node>
		defaultMechMap = new EnumMap<>(FocalMech.class);
		defaultMechMap.put(STRIKE_SLIP, 0.334);
		defaultMechMap.put(REVERSE, 0.333);
		defaultMechMap.put(NORMAL, 0.333);

		//@formatter:off
		LocationList border = LocationList.create(
			Location.create(39.000, -119.999),
			Location.create(35.000, -114.635),
			Location.create(34.848, -114.616),
			Location.create(34.719, -114.482),
			Location.create(34.464, -114.371),
			Location.create(34.285, -114.122),
			Location.create(34.097, -114.413),
			Location.create(33.934, -114.519),
			Location.create(33.616, -114.511),
			Location.create(33.426, -114.636),
			Location.create(33.401, -114.710),
			Location.create(33.055, -114.676),
			Location.create(33.020, -114.501),
			Location.create(32.861, -114.455),
			Location.create(32.741, -114.575),
			Location.create(32.718, -114.719),
			Location.create(32.151, -120.861),
			Location.create(39.000, -126.000),
			Location.create(42.001, -126.000),
			Location.create(42.001, -119.999),
			Location.create(39.000, -119.999));
		CA_REGION = Regions.create("California Border", border, BorderType.MERCATOR_LINEAR);
		//@formatter:off
	}


	private SystemGridConverter() {};

	static SystemGridConverter create() {
		SystemGridConverter igc = new SystemGridConverter();
		return igc;
	}

	void process(Path solPath, Path outDir) throws Exception {

		String solName = solPath.getFileName().toString();
		solName = solName.substring(0, solName.lastIndexOf('.'));
		Path brAvgId = solPath.getParent().getFileName();
		Path solDir = outDir.resolve(brAvgId).resolve(SYSTEM.toString()).resolve(solName);
		Files.createDirectories(solDir);
		File gridOut = solDir.resolve(GRID_XML).toFile();

		ZipFile zip = new ZipFile(solPath.toString());
		ZipEntry entry = zip.getEntry(GRID_XML);
		
		System.out.println("Processing grid file...");
		
		double weight = SystemConverter.computeWeight(solName);
		
		if (entry != null) {
			System.out.println("    File format: xml");
			InputStream gridIn = zip.getInputStream(entry);
			processGridFileXml(gridIn, gridOut, solName, weight);
		} else {
			System.out.println("    File format: bin");
			entry = zip.getEntry(GRID_BIN);
			InputStream gridIn = zip.getInputStream(entry);
			processGridFileBin(gridIn, gridOut, solName, weight);
		}
		System.out.println("         Weight: " + weight);

		zip.close();
	}

	// when kevin converted to the binary format, mfds were reduced to
	// mag range 5.05 to 8.95; we further reduce to 5.05 to 7.85 ([29])
	private void processGridFileBin(InputStream in, File out, String id, double weight)
			throws ParserConfigurationException,
			TransformerConfigurationException, TransformerException,
			IOException {
				
        GriddedRegion gr = Utils.RELM_Region(0.1);
        
        // file out
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document docOut = dBuilder.newDocument();
        Element rootOut = docOut.createElement(GRID_SOURCE_SET.toString());
        docOut.appendChild(rootOut);
        addAttribute(NAME, id, rootOut);
        addAttribute(WEIGHT, weight, rootOut);
        
        Element settings = addElement(SETTINGS, rootOut);
		addDefaultMfds(settings);
		addSourceProperties(settings);
		
        List<Double> magList = Doubles.asList(mags);
        Element nodesOut = addElement(NODES, rootOut);
        
        // data in
        List<List<Double>> rates = readRates(in);
        for (int i=0; i<rates.size(); i++) {
        	Location loc = gr.locationForIndex(i);

        	// filter sources outside CA border for UC3-NSHMP compatibility
        	if (!CA_REGION.contains(loc)) continue;
        	
        	List<Double> rateList = rates.get(i).subList(0, mags.length);
        	// filter aftershocks via rate reduction
        	rateList = removeAftershocks(magList, rateList);
        	
        	Element nodeOut = addElement(NODE, nodesOut);
        	nodeOut.setTextContent(Utils.locToString(loc));
			addAttribute(RATES, Parsing.toString(rateList, "%.8g"), nodeOut);
			addAttribute(TYPE, INCR, nodeOut);
			addAttribute(FOCAL_MECH_MAP, enumValueMapToString(createMechMap(i)), nodeOut);
        }
       
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
 	}
		
	private List<List<Double>> readRates(InputStream in) throws IOException {
		if (!(checkNotNull(in) instanceof BufferedInputStream)) {
			in = new BufferedInputStream(in);
		}
		DataInputStream din = new DataInputStream(in);
		int count = din.readInt();
		int arraySize = din.readInt();
		
		for (int i=0; i<arraySize; i++) {
			// step through mag array
			din.readDouble();
		}
		
		List<List<Double>> rates = new ArrayList<>();
		for (int i=0; i<count - 1; i+=2) {
			List<Double> nodeRates = Doubles.asList(new double[arraySize]);
			for (int j = 0; j < 2; j++) {
				int rateSize = din.readInt();
				for (int k = 0; k < rateSize; k++) {
					double rate = din.readDouble();
					nodeRates.set(k, nodeRates.get(k) + rate);
				}
			}
			rates.add(nodeRates);
		}
		in.close();
		return rates;
	}

	/*
	 * Converts and condenses a grid file that accompanies a UC3 branch average
	 * solution. These are indexed according to the RELM_GRIDDED region and can
	 * therefore be processed and output in order and maintin consistency with
	 * desired grid source output format. We can also compose focal mech maps in
	 * correct order as the focal mech dat files are similarly ordered.
	 */
	private void processGridFileXml(InputStream in, File out, String id, double weight)
			throws ParserConfigurationException, SAXException,
			TransformerConfigurationException, TransformerException,
			IOException {
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(in);
		docIn.getDocumentElement().normalize();
		
        GriddedRegion gr = Utils.RELM_Region(0.1);
        
        // file out
        Document docOut = dBuilder.newDocument();
        Element rootOut = docOut.createElement(GRID_SOURCE_SET.toString());
        docOut.appendChild(rootOut);
        addAttribute(NAME, id, rootOut);
        addAttribute(WEIGHT, weight, rootOut);
        
        Element settings = addElement(SETTINGS, rootOut);
		addDefaultMfds(settings);
		addSourceProperties(settings);
				
        List<Double> magList = Doubles.asList(mags);
        Element nodesOut = addElement(NODES, rootOut);
        
        // file in
        Element rootIn = docIn.getDocumentElement();
        Element nodeList = (Element) rootIn.getElementsByTagName("MFDNodeList").item(0);
        NodeList nodesIn = nodeList.getElementsByTagName("MFDNode");
        for (int i=0; i<nodesIn.getLength(); i++) {
        	Element node = (Element) nodesIn.item(i);
        	int nodeIndex = Integer.parseInt(node.getAttribute("index"));
        	Location loc = gr.locationForIndex(nodeIndex);

        	// filter sources outside CA border for UC3-NSHMP compatibility
        	if (!CA_REGION.contains(loc)) continue;
        	
        	// filter aftershocks via rate reduction
        	List<Double> rates = processGridNode(node);
        	rates = removeAftershocks(magList, rates);
        	
        	Element nodeOut = addElement(NODE, nodesOut);
        	nodeOut.setTextContent(Utils.locToString(loc));
			addAttribute(RATES, Parsing.toString(rates, "%.8g"), nodeOut);
			addAttribute(TYPE, INCR, nodeOut);
			addAttribute(FOCAL_MECH_MAP, enumValueMapToString(createMechMap(i)), nodeOut);
        }
       
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
 	}
	
	private static void addDefaultMfds(Element e) {
        Element mfdRef = addElement(DEFAULT_MFDS, e);
        Element mfd = addElement(INCREMENTAL_MFD, mfdRef);
        addAttribute(TYPE, INCR, mfd);
        List<Double> magList = Doubles.asList(mags);
		addAttribute(MAGS, Parsing.toString(magList, "%.2f"), mfd);
		List<Double> ratesRef = Doubles.asList(new double[mags.length]);
		addAttribute(RATES, Parsing.toString(ratesRef, "%.1f"), mfd);
		addAttribute(WEIGHT, 1.0, mfd);
	}

	private static void addSourceProperties(Element e) {
		Element propsElem = addElement(SOURCE_PROPERTIES, e);
		String magDepthData = GridSourceData.magDepthDataToString(6.5, new double[] {5.0, 1.0});
		addAttribute(MAG_DEPTH_MAP, magDepthData, propsElem);
		addAttribute(FOCAL_MECH_MAP, enumValueMapToString(defaultMechMap), propsElem);
		addAttribute(STRIKE, Double.NaN, propsElem);
		addAttribute(RUPTURE_SCALING, NSHM_POINT_WC94_LENGTH, propsElem);
		addAttribute(MAX_DEPTH, 14.0, propsElem);
	}

	private static List<Double> processGridNode(Element e) {
		NodeList unassocElem = e.getElementsByTagName("UnassociatedFD");
		checkState(unassocElem.getLength() <= 1);
		List<Double> unassocRates = null;
		if (unassocElem.getLength() == 1) {
			unassocRates = mfdRates((Element) unassocElem.item(0));
		}

		NodeList subSeisElem = e.getElementsByTagName("SubSeisMFD");
		checkState(subSeisElem.getLength() <= 1);
		List<Double> subSeisRates = null;
		if (subSeisElem.getLength() == 1) {
			subSeisRates = mfdRates((Element) subSeisElem.item(0));
		}

		checkState(unassocRates != null || subSeisRates != null);

		if (unassocRates == null) return subSeisRates;
		if (subSeisRates == null) return unassocRates;
		return DataUtils.add(unassocRates, subSeisRates);
	}

	private static List<Double> mfdRates(Element e) {
		NodeList points = ((Element) e.getElementsByTagName("Points").item(0))
			.getElementsByTagName("Point");
		List<Double> rates = Lists.newArrayList();
		for (int i = 0; i < points.getLength(); i++) {
			Element point = (Element) points.item(i);
			String mag = String.format("%.2f", Double.parseDouble(point.getAttribute("x")));
			if (magStrSet.contains(mag)) {
				rates.add(Double.parseDouble(point.getAttribute("y")));
			}
		}
		return rates;
	}

	private static Map<FocalMech, Double> createMechMap(int index) {
		Map<FocalMech, Double> map = Maps.newEnumMap(FocalMech.class);
		map.put(STRIKE_SLIP, fracStrikeSlip[index]);
		map.put(REVERSE, fracReverse[index]);
		map.put(NORMAL, fracNormal[index]);
		return map;
	}

	private static final String MECH_DATA_DIR = "../../svn/OpenSHA/dev/scratch/UCERF3/data/seismicityGrids/";

	private static double[] readFocalMechs(String filename) throws IOException {
		Path mechPath = Paths.get(MECH_DATA_DIR, filename);
		List<String> lines = Files.readAllLines(mechPath, StandardCharsets.US_ASCII);
		double[] data = new double[lines.size()];
		int count = 0;
		for (String line : lines) {
			data[count] = Double.valueOf(Iterables.get(Parsing.split(line, Delimiter.SPACE), 2));
			count++;
		}
		return data;
	}
	
	private static List<Double> removeAftershocks(List<Double> mags, List<Double> rates) {
		checkArgument(mags.size() == rates.size());
		for (int i=0; i<rates.size(); i++) {
			rates.set(i, SystemAftershockFilter.scaleGridRate(mags.get(i), rates.get(i)));
		}
		return rates;
	}
	
}
