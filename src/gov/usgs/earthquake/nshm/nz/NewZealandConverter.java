package gov.usgs.earthquake.nshm.nz;

import static org.opensha.gmm.Gmm.*;
import static com.google.common.base.Preconditions.checkState;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.NN;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.NV;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.RV;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.RO;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.SR;
import static gov.usgs.earthquake.nshm.nz.NZ_SourceID.SS;
import static org.opensha.eq.TectonicSetting.ACTIVE_SHALLOW_CRUST;
import static org.opensha.eq.TectonicSetting.SUBDUCTION_INTERFACE;
import static org.opensha.eq.TectonicSetting.VOLCANIC;
import static org.opensha.eq.fault.scaling.MagScalingType.WC_94_LENGTH;
import static org.opensha.eq.forecast.SourceAttribute.DEPTH;
import static org.opensha.eq.forecast.SourceAttribute.DIP;
import static org.opensha.eq.forecast.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha.eq.forecast.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha.eq.forecast.SourceAttribute.MAG_SCALING;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.RAKE;
import static org.opensha.eq.forecast.SourceAttribute.STRIKE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceAttribute.WIDTH;
import static org.opensha.eq.forecast.SourceElement.FAULT_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.GEOMETRY;
import static org.opensha.eq.forecast.SourceElement.GRID_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST_REF;
import static org.opensha.eq.forecast.SourceElement.NODE;
import static org.opensha.eq.forecast.SourceElement.NODES;
import static org.opensha.eq.forecast.SourceElement.SETTINGS;
import static org.opensha.eq.forecast.SourceElement.SOURCE;
import static org.opensha.eq.forecast.SourceElement.SOURCE_PROPERTIES;
import static org.opensha.eq.forecast.SourceElement.SUBDUCTION_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.TRACE;
import static org.opensha.eq.forecast.SourceType.FAULT;
import static org.opensha.eq.forecast.SourceType.GRID;
import static org.opensha.eq.forecast.SourceType.INTERFACE;
import static org.opensha.eq.forecast.SourceType.SLAB;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;
import static org.opensha.util.Parsing.enumValueMapToString;
import gov.usgs.earthquake.nshm.convert.CH_Data;
import gov.usgs.earthquake.nshm.convert.GR_Data;
import gov.usgs.earthquake.nshm.nz.NewZealandParser.FaultData;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
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

import org.opensha.eq.TectonicSetting;
import org.opensha.geo.Location;
import org.opensha.gmm.Gmm;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multisets;
import com.google.common.math.DoubleMath;

/*
 * Convert New Zealand source files to NSHM compatible forecast.
 * 
 * @author Peter Powers
 */
class NewZealandConverter {


	private static final Path FCAST_DIR = Paths.get("forecasts", "nz");
	private static final String GMM_FILE = "gmm.xml";

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
		converter.addGmms();
	}
	
	private static final Map<Gmm, Double> volcGmms = ImmutableMap.of(MCV, v1)

	private void convertFault(Path outDir) throws Exception {

		// faults
		for (TectonicSetting tect : parser.tectonicSettings()) {
			if (tect == VOLCANIC) {

				Path outPath = Paths.get(FAULT.toString(), "volcanic", "sources.xml");
				Path out = outDir.resolve(outPath);
				FaultExporter export = new FaultExporter(false);
				export.faultDataList = parser.getSourceDataList(tect);
				export.setName = "Volcanic Sources";
				export.chRef = CH_Data.create(7.5, 0.0, 1.0, false);
				export.writeXML(out);

			} else if (tect == ACTIVE_SHALLOW_CRUST) {

				Path outPath = Paths.get(FAULT.toString(), "tectonic", "sources.xml");
				Path out = outDir.resolve(outPath);
				FaultExporter export = new FaultExporter(false);
				export.faultDataList = parser.getSourceDataList(tect);
				export.setName = "Tectonic Sources";
				export.chRef = CH_Data.create(7.5, 0.0, 1.0, false);
				export.writeXML(out);

			} else if (tect == SUBDUCTION_INTERFACE) {

				Path outPath = Paths.get(INTERFACE.toString(), "sources.xml");
				Path out = outDir.resolve(outPath);
				FaultExporter export = new FaultExporter(true);
				export.faultDataList = parser.getSourceDataList(tect);
				export.setName = "Interface Sources";
				export.chRef = CH_Data.create(7.5, 0.0, 1.0, false);
				export.writeXML(out);

			} else {
				throw new IllegalStateException(tect.toString());
			}
		}
	}
	
	private void convertGrid(Path outDir) throws Exception {
		
//		Set<Double> depthTest = Sets.newHashSet();
//		for (Location loc : parser.locs) {
//			depthTest.add(loc.depth());
//		}
//		System.out.println(depthTest);
//		Set<NZ_SourceID> typeTest = Sets.newHashSet();
//		for (NZ_SourceID id : parser.ids) {
//			typeTest.add(id);
//		}
//		System.out.println(typeTest);
		
		// loop types and depths
		Set<NZ_SourceID> ids = EnumSet.of(NV, RV, SR, SS, NN);
		Set<Double> depths = ImmutableSet.of(10.0, 30.0, 50.0, 70.0, 90.0);
		
		/*
		 * Split depths at 40km; below = slab, above = crustal | volcanic
		 * TODO this may be incorrect; check with Stirling
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
				
			}
		}
		// ensure all lines in parser were processed
		checkState(count == parser.locs.size(), "Only processed %s of %s sources", count,
				parser.locs.size());

	}
	
	private static String createGridName(NZ_SourceID id, double depth) {
		String tect = ((depth < 40.0) ? (id == NV) ? "Volcanic Grid " : "Tectonic Grid " : "Slab ");
		String depthStr = " [" + ((int) depth) + "km";
		String style = (id == NV || id == NN) ? "; normal]" : (id == RV) ? "; reverse]"
			: (id == SR) ? "; reverse-oblique]" : "; strike-slip]";
		return tect + "Sources" + depthStr + style;
	}
	
	private static Path createGridPath(NZ_SourceID id, double depth) {
		String name = "sources-" + id.toString().toLowerCase()  + "-" + ((int) depth) + "km.xml";
		String dir = (depth < 40.0) ? GRID.toString() : SLAB.toString();
		String subDir = (depth < 40.0) ? (id == NV) ? "volcanic" : "tectonic" : "";
		return Paths.get(dir, subDir, name);
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
		Element mfdRef = addElement(MAG_FREQ_DIST_REF, settings);
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
		addAttribute(MAG_DEPTH_MAP, magDepthDataToString(10.0, new double[] {depth, depth}), propsElem);
		addAttribute(FOCAL_MECH_MAP, enumValueMapToString(id.mechWtMap()), propsElem);
		addAttribute(STRIKE, Double.NaN, propsElem);
		addAttribute(MAG_SCALING, WC_94_LENGTH, propsElem);
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
			 Element mfdRef = addElement(MAG_FREQ_DIST_REF, settings);
			 chRef.appendTo(mfdRef, null);
			 
			 // source properties
			 Element propsElem = addElement(SOURCE_PROPERTIES, settings);
			 addAttribute(MAG_SCALING, WC_94_LENGTH, propsElem);

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