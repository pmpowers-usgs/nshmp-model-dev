package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha.eq.model.SourceAttribute.A;
import static org.opensha.eq.model.SourceAttribute.B;
import static org.opensha.eq.model.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha.eq.model.SourceAttribute.MAGS;
import static org.opensha.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha.eq.model.SourceAttribute.FOCAL_MECH_MAP;
import static org.opensha.eq.model.SourceAttribute.M_MAX;
import static org.opensha.eq.model.SourceAttribute.NAME;
import static org.opensha.eq.model.SourceAttribute.RATES;
import static org.opensha.eq.model.SourceAttribute.STRIKE;
import static org.opensha.eq.model.SourceAttribute.TYPE;
import static org.opensha.eq.model.SourceAttribute.WEIGHT;
import static org.opensha.eq.model.SourceElement.GRID_SOURCE_SET;
import static org.opensha.eq.model.SourceElement.INCREMENTAL_MFD;
import static org.opensha.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha.eq.model.SourceElement.NODE;
import static org.opensha.eq.model.SourceElement.NODES;
import static org.opensha.eq.model.SourceElement.SETTINGS;
import static org.opensha.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha.mfd.MfdType.GR;
import static org.opensha.mfd.MfdType.GR_TAPER;
import static org.opensha.mfd.MfdType.INCR;
import static org.opensha.mfd.MfdType.SINGLE;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;
import static org.opensha.util.Parsing.enumValueMapToString;
import gov.usgs.earthquake.nshm.util.FaultCode;
import gov.usgs.earthquake.nshm.util.RateType;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

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

import org.opensha.data.DataUtils;
import org.opensha.eq.fault.FocalMech;
import org.opensha.eq.model.RuptureScaling;
import org.opensha.geo.GriddedRegion;
import org.opensha.geo.Location;
import org.opensha.mfd.GutenbergRichterMfd;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.mfd.MfdType;
import org.opensha.mfd.Mfds;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Predicate;
import com.google.common.collect.Range;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

/*
 * Slab source data container for 2014 required by use of stair-step
 * depth model wherein a single fortran input is split into three
 * dept-specific files. Much of this class is not relevant to or
 * used when handling the slab-depth issue.
 * 
 * @author Peter Powers
 */
class SlabSourceData2014 {

	String name;
	double weight;

	Map<Double, Range<Double>> lonDepthMap;
	double[] depths;
	double depthMag;
	Map<FocalMech, Double> mechWtMap;

	GR_Data grDat;
	CH_Data chDat;

	double dR, rMax;

	double minLat, maxLat, dLat;
	double minLon, maxLon, dLon;

	FaultCode fltCode;
	boolean bGrid, mMaxGrid, weightGrid;
	double mTaper;
	
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
	 * @throws ParserConfigurationException
	 * @throws TransformerConfigurationException
	 * @throws TransformerException
	 */
	public void writeXML(File out, Range<Double> lonRange, double depth) throws
			ParserConfigurationException,
			TransformerConfigurationException, TransformerException {

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(GRID_SOURCE_SET.toString());
		addAttribute(NAME, name, root);
		addAttribute(WEIGHT, weight, root);
		doc.appendChild(root);
		
		writeStandardGrid(root, lonRange, depth);
		
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
	private void writeStandardGrid(Element root, Range<Double> lonRange, double depth) {
		Element settings = addElement(SETTINGS, root);
		Element mfdRef = addElement(DEFAULT_MFDS, settings);
		grDat.appendTo(mfdRef, null);
		addSourceProperties(settings);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Location loc = region.locationForIndex(i);
			if (!lonRange.contains(loc.lon())) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(loc));
			writeStandardMFDdata(nodeElem, i);
		}
	}
	
	private void writeStandardMFDdata(Element nodeElem, int i) {
		MfdType type = grDat.cMag > 6.5 ? GR_TAPER : GR;
		addAttribute(TYPE, type, nodeElem);
		addAttribute(A, aDat[i], "%.8g", nodeElem);
		if (bGrid) {
			double nodebVal = bDat[i];
			if (!DoubleMath.fuzzyEquals(nodebVal, grDat.bVal, 0.000001)) {
				addAttribute(B, nodebVal, "%.6f", nodeElem);
			}
		}
		if (mMaxGrid) {
			double nodeMMax = mMaxDat[i] - grDat.dMag / 2.0;
			if (!DoubleMath.fuzzyEquals(nodeMMax, grDat.mMax, 0.000001) && nodeMMax != 0.0) {
				addAttribute(M_MAX, nodeMMax, "%.6f", nodeElem);
			}
		}
	}
	
	// source attribute settings
	private void addSourceProperties(Element settings) {
		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(MAG_DEPTH_MAP, magDepthDataToString(depthMag, depths), propsElem);
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
			.append("Slab Config").append(LF)
			.append("            Name: ").append(name).append(LF)
			.append("       Lat range: ").append(minLat).append(" ").append(maxLat).append(LF)
			.append("       Lon range: ").append(minLon).append(" ").append(maxLon).append(LF)
			.append("     [dLat dLon]: ").append(dLat).append(" ").append(dLon).append(LF)
			.append("      zTop byLon: ").append(lonDepthMap).append(LF)
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
