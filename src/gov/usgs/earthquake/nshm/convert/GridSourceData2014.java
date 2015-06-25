package gov.usgs.earthquake.nshm.convert;

import static org.opensha2.eq.fault.FocalMech.NORMAL;
import static org.opensha2.eq.fault.FocalMech.REVERSE;
import static org.opensha2.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha2.eq.model.SourceAttribute.A;
import static org.opensha2.eq.model.SourceAttribute.B;
import static org.opensha2.eq.model.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha2.eq.model.SourceAttribute.ID;
import static org.opensha2.eq.model.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha2.eq.model.SourceAttribute.MAX_DEPTH;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RATE;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.eq.model.SourceAttribute.STRIKE;
import static org.opensha2.eq.model.SourceAttribute.TYPE;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha2.eq.model.SourceElement.GRID_SOURCE_SET;
import static org.opensha2.eq.model.SourceElement.NODE;
import static org.opensha2.eq.model.SourceElement.NODES;
import static org.opensha2.eq.model.SourceElement.SETTINGS;
import static org.opensha2.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha2.mfd.MfdType.GR;
import static org.opensha2.mfd.MfdType.GR_TAPER;
import static org.opensha2.mfd.MfdType.SINGLE;
import static org.opensha2.util.Parsing.addAttribute;
import static org.opensha2.util.Parsing.addComment;
import static org.opensha2.util.Parsing.addElement;
import static org.opensha2.util.Parsing.enumValueMapToString;
import gov.usgs.earthquake.nshm.util.FaultCode;
import gov.usgs.earthquake.nshm.util.RateType;
import gov.usgs.earthquake.nshm.util.SourceRegion;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.eq.model.SourceType;
import org.opensha2.geo.GriddedRegion;
import org.opensha2.mfd.MfdType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.collect.Multiset;
import com.google.common.math.DoubleMath;

/*
 * Grid source data container.
 * 
 * @author Peter Powers
 */
class GridSourceData2014 {

	String name; // original name
	int id;
	String displayName;
	String fileName;
	double weight;

	SourceRegion srcRegion;
	SourceType srcType;

	double[] depths;
	double depthMag;
	double maxDepth;
	Map<FocalMech, Double> mechWtMap;

	GR_Data grDat;
	CH_Data chDat;
	Set<CH_Data> chDats;

	double dR, rMax;

	double minLat, maxLat, dLat;
	double minLon, maxLon, dLon;

	FaultCode fltCode;
	boolean bGrid, mMaxGrid, weightGrid;
	double mTaper;
	List<Map<Double, Double>> mMaxWtMaps;
	Multiset<Double> mMaxZoneBag;

	// we're now ignoring mTaper in favor of using
	// incremental MFDs where appropriate/necessary

	URL aGridURL, bGridURL, mMaxGridURL, weightGridURL;

	double timeSpan;
	RateType rateType;

	double strike = Double.NaN;

	RuptureScaling rupScaling;

	GriddedRegion region;
	double[] aDat, bDat, mMaxDat, wgtDat;

	private static final String LF = System.getProperty("line.separator");

	// @formatter:off

	/**
	 * Write grid data to XML.
	 * 
	 * @param out file
	 * @param mMaxIndex 
	 * @throws ParserConfigurationException
	 * @throws TransformerConfigurationException
	 * @throws TransformerException
	 */
	public void writeXML(File out, int mMaxIndex) throws ParserConfigurationException,
			TransformerConfigurationException, TransformerException {

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(GRID_SOURCE_SET.toString());
		addAttribute(NAME, displayName, root);
		addAttribute(ID, id, root);
		addAttribute(WEIGHT,  DataUtils.clean(8, weight)[0], root);
		Converter.addDisclaimer(root);
		addComment(" Original source file: " + name + " ", root);
		doc.appendChild(root);
		
		if (mMaxIndex >= 0) {
			// non-negative index indicates an mMax zone
			writeZoneGrid(root, mMaxIndex);
		} else {
			// ignore index and write file with multiple SINGLE mfds
			writeRlmeGrid(root);
		}
		
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);

		trans.transform(source, result);
	}
	
	// standard grid without customizations requiring incremental MFDs
	private void writeRlmeGrid(Element root) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		for (CH_Data chDat : chDats) {
			chDat.appendTo(mfdRef, null);
		}
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			writeRlmeMFDdata(nodeElem, i);
		}
	}

	private void writeRlmeMFDdata(Element nodeElem, int i) {
		MfdType type = SINGLE;
		addAttribute(TYPE, type, nodeElem);
		addAttribute(RATE, aDat[i], "%.8g", nodeElem);
		// no mMax of bGrids
	}

	// standard grid without customizations requiring incremental MFDs
	private void writeZoneGrid(Element root, int index) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		for (Entry<Double, Double> entry : mMaxWtMaps.get(index).entrySet()) {
			grDat.mMax = entry.getKey() - grDat.dMag / 2.0;
			grDat.weight = entry.getValue();
			grDat.appendTo(mfdRef, null);
		}
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			double mMaxFlagIndex = ((int) Math.rint(mMaxDat[i])) - 1;
			// only build for the specified zone
			if (mMaxFlagIndex != index) continue; 
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			writeZoneMFDdata(nodeElem, i);
		}
	}
	
	private void writeZoneMFDdata(Element nodeElem, int i) {
		MfdType type = grDat.cMag > 6.5 ? GR_TAPER : GR;
		addAttribute(TYPE, type, nodeElem);
		addAttribute(A, aDat[i], "%.8g", nodeElem);
		if (bGrid) {
			double nodebVal = bDat[i];
			if (!DoubleMath.fuzzyEquals(nodebVal, grDat.bVal, 0.000001)) {
				addAttribute(B, nodebVal, "%.6f", nodeElem);
			}
		}
		// never any mMax grid
	}
	
	// source attribute settings
	private void addSourceProperties(Element settings) {
		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(MAG_DEPTH_MAP, magDepthDataToString(depthMag, depths), propsElem);
		addAttribute(MAX_DEPTH, maxDepth, propsElem);
		addAttribute(FOCAL_MECH_MAP, enumValueMapToString(mechWtMap), propsElem);
		addAttribute(STRIKE, strike, propsElem);
		addAttribute(RUPTURE_SCALING, rupScaling, propsElem);
	}
			
	/*
	 * This actually reproduces something closer to the originally supplied
	 * NSHMP mag-depth-weight distribution, but it's not worth going back to
	 * the parser to change it. Example outputs that can be parsed as
	 * stringToValueValueWeightMap:
	 * 		[6.5::[5.0:1.0]; 10.0::[1.0:1.0]]	standard two depth
	 * 		[10.0::[50.0:1.0]]					standard single depth
	 */
	static String magDepthDataToString(double mag, double[] depths) {
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

	@Override
	public String toString() {		

		StringBuilder sb = new StringBuilder()
			.append("Grid Config").append(LF)
			.append("            Name: ").append(name).append(LF)
			.append("       Lat range: ").append(minLat).append(" ").append(maxLat).append(LF)
			.append("       Lon range: ").append(minLon).append(" ").append(maxLon).append(LF)
			.append("     [dLat dLon]: ").append(dLat).append(" ").append(dLon).append(LF)
			.append("   Rup top M<6.5: ").append(depths[0]).append(LF)
			.append("   Rup top M≥6.5: ").append(depths[1]).append(LF)
			.append("    Mech weights: ")
			.append("SS=").append(mechWtMap.get(STRIKE_SLIP))
			.append(" R=").append(mechWtMap.get(REVERSE))
			.append(" N=").append(mechWtMap.get(NORMAL)).append(LF)
			.append("   opt [dR rMax]: ").append(dR).append(" ").append(rMax).append(LF);
		if (chDat != null) {
			sb.append("    SINGLE [a M]: ").append(chDat.rate).append(" ").append(chDat.mag).append(LF);
		} else {
			sb.append(" GR [b M- M+ dM]: ").append(grDat.bVal).append(" ").append(grDat.mMin)
				.append(" ").append(grDat.mMax).append(" ").append(grDat.dMag).append(LF);
		}
		sb.append("          a grid: ").append(aGridURL.toString()).append(LF)
			.append("          b grid: ").append(bGrid)
			.append(" ").append((bGridURL != null) ? bGridURL.toString() : "").append(LF)
			.append("       mMax grid: ").append(mMaxGrid)
			.append(" ").append((mMaxGridURL != null) ? mMaxGridURL.toString() : "").append(LF)
			.append("mMax zone counts: ").append(mMaxZoneBag).append(LF)
			.append("     weight grid: ").append(weightGrid)
			.append(" ").append((weightGridURL != null) ? weightGridURL.toString() : "").append(LF)
			.append("         M taper: ").append(mTaper).append(LF)
			.append("       Time span: ").append(timeSpan).append(LF)
			.append("            Rate: ").append(rateType).append(LF)
			.append("      Fault Code: ").append(fltCode).append(LF)
			.append("          Strike: ").append(strike).append(LF);
		return sb.toString();
	}
	
}
