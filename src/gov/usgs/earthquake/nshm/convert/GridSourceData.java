package gov.usgs.earthquake.nshm.convert;

import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha.eq.forecast.MFD_Attribute.MAGS;
import static org.opensha.eq.forecast.MFD_Attribute.RATES;
import static org.opensha.eq.forecast.MFD_Attribute.TYPE;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST_REF;
import static org.opensha.eq.forecast.SourceElement.GRID_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.NODE;
import static org.opensha.eq.forecast.SourceElement.NODES;
import static org.opensha.eq.forecast.SourceElement.SOURCE_ATTS;
import static org.opensha.util.Parsing.addElement;
import static org.opensha.util.Parsing.enumValueMapToString;
import gov.usgs.earthquake.nshm.util.FaultCode;
import gov.usgs.earthquake.nshm.util.RateType;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.net.URL;
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
import org.opensha.eq.forecast.SourceElement;
import org.opensha.geo.GriddedRegion;
import org.opensha.mfd.GutenbergRichterMFD;
import org.opensha.mfd.IncrementalMFD;
import org.opensha.mfd.MFD_Type;
import org.opensha.mfd.MFDs;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

/*
 * Grid source data container.
 * @author Peter Powers
 */
class GridSourceData {

	String name;
	double weight;

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
	public void writeXML(File out) throws ParserConfigurationException,
			TransformerConfigurationException, TransformerException {

		DocumentBuilderFactory docFactory = DocumentBuilderFactory
			.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		Element root = doc.createElement(GRID_SOURCE_SET.toString());
		root.setAttribute("file", name);
		root.setAttribute("weight", Double.toString(weight));
		doc.appendChild(root);
		
		if (chDat != null) { // single mag defaults e.g. charleston
			writeSingleMagGrid(root);
		} else if (name.contains("2007all8")) { // large all8 CEUS grids
			writeLargeCeusGrid(root);
		} else if (weightGrid) { // WUS grids with downweighted rates above 6.5 in CA
			writeMixedGrid(root);
		} else {
			writeStandardGrid(root);
		}
		
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(out);

		trans.transform(source, result);
	}
	
	// single magnitude grids (e.e.g Charleston)
	private void writeSingleMagGrid(Element root) {
		Element mfdRef = addElement(MAG_FREQ_DIST_REF, root);
		chDat.appendDefaultTo(mfdRef);
		addSourceAttributes(mfdRef);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			double singleMagRate = MFDs.incrRate(aVal, grDat.bVal, chDat.mag);
			nodeElem.setAttribute("a", String.format("%.8g",singleMagRate));
		}
	}
	
	// large mblg CEUS grids with craton-margin tapers etc...
	private void writeLargeCeusGrid(Element root) {
		Element defaults = addElement(MAG_FREQ_DIST_REF, root);
		Element e = Parsing.addElement(SourceElement.MAG_FREQ_DIST, defaults);
		e.setAttribute(TYPE.toString(), MFD_Type.INCR.name());
		e.setAttribute(MAGS.toString(), Parsing.toString(Doubles.asList(
			name.contains(".AB.") ? abMags : jMags), "%.2f"));
		addSourceAttributes(defaults);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			addCEUS_MFD(i, nodeElem);
		}
	}
	
	// for grids with wtGrid
	private void writeMixedGrid(Element root) {
		Element defaults = addElement(MAG_FREQ_DIST_REF, root);
		grDat.appendDefaultTo(defaults);
		Element e = Parsing.addElement(SourceElement.MAG_FREQ_DIST, defaults);
		e.setAttribute(TYPE.toString(), MFD_Type.INCR.name());
		// default mags go up to default grid mMax; mags will be overridden
		// where node mMax is higher
		double[] mags = DataUtils.buildSequence(grDat.mMin, grDat.mMax, 0.1, true);
		e.setAttribute(MAGS.toString(), Parsing.toString(Doubles.asList(mags), "%.2f"));
		addSourceAttributes(defaults);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));

			double nodeWt = wgtDat[i];
			boolean wtIsOne = DoubleMath.fuzzyEquals(nodeWt, 1.0, 0.00000001);
			// weight doesn't apply because mMax <= mTaper
			boolean ignoreWt = mMaxDat[i] <= mTaper;
			if (wtIsOne || ignoreWt) {
				writeStandardMFDdata(nodeElem, i);
				nodeElem.setAttribute(TYPE.toString(), MFD_Type.GR.name());
			} else {
				addWUS_MFD(i, nodeElem);
				nodeElem.setAttribute(TYPE.toString(), MFD_Type.INCR.name());
			}
		}
		
	}
	
	// standard grid without customizations requiring incremental MFDs
	private void writeStandardGrid(Element root) {
		Element mfdRef = addElement(MAG_FREQ_DIST_REF, root);
		grDat.appendTo(mfdRef);
		addSourceAttributes(mfdRef);
		Element nodesElem = addElement(NODES, root);
		for (int i=0; i<aDat.length; i++) {
			double aVal = aDat[i];
			if (aVal <= 0.0) continue;
			Element nodeElem = addElement(NODE, nodesElem);
			nodeElem.setTextContent(Utils.locToString(region.locationForIndex(i)));
			writeStandardMFDdata(nodeElem, i);
		}
	}
	
	private void writeStandardMFDdata(Element nodeElem, int i) {
		nodeElem.setAttribute("a", String.format("%.8g",aDat[i]));
		if (bGrid) {
			double nodebVal = bDat[i];
			if (!DoubleMath.fuzzyEquals(nodebVal, grDat.bVal, 0.000001)) {
				nodeElem.setAttribute("b", Parsing.stripZeros(
					String.format("%.6f", nodebVal)));
			}
		}
		if (mMaxGrid) {
			double nodeMMax = mMaxDat[i] - grDat.dMag / 2.0;
			if (!DoubleMath.fuzzyEquals(nodeMMax, grDat.mMax, 0.000001) 
					&& nodeMMax != 0.0) {
				nodeElem.setAttribute("mMax", Parsing.stripZeros(
					String.format("%.6f",nodeMMax)));
			}
		}
	}
	
	// default source settings
	private void addSourceAttributes(Element defaults) {
		Element attsElem = addElement(SOURCE_ATTS, defaults);
		attsElem.setAttribute("depthMap", magDepthDataToString(depthMag, depths));
		if (!Double.isNaN(strike)) {
			attsElem.setAttribute("strike", Double.toString(strike));
		}
		attsElem.setAttribute("mechs", enumValueMapToString(mechWtMap));
	}
			
	/*
	 * This actually reproduces something closer to the originally supplied
	 * NSHMP mag-depth-weight distribution, but it's not worth going back to
	 * the parser to change it. Example outputs that can be parsed as
	 * stringToValueValueWeightMap:
	 * 		[6.5::[5.0:1.0]; 10.0::[1.0:1.0]]	standard two depth
	 * 		[10.0::[50.0:1.0]]					standard single dpeth
	 */
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

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
			.append("Grid Config").append(LF)
			.append("            Name: ").append(name).append(LF)
			.append("       Lat range: ").append(minLat).append(" ").append(maxLat).append(LF)
			.append("       Lon range: ").append(minLon).append(" ").append(maxLon).append(LF)
			.append("     [dLat dLon]: ").append(dLat).append(" ").append(dLon).append(LF)
			.append("   Rup top M\u003C6.5: ").append(depths[0]).append(LF)
			.append("   Rup top M\u22656.5: ").append(depths[1]).append(LF)
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
	
	
	/////////////// WUS Incremental ///////////////
	
	// there are many nodes where mMax = 6.5 and a weight is applied that ultimately is unnecessary
	// build incremental default mags based on mMax of file; if node mMax is greater, then add mags attribute to it
	
	private void addWUS_MFD(int i, Element node) {
		double cutoffMax = mMaxDat[i] <= 0 ? grDat.mMax + grDat.dMag / 2. : mMaxDat[i];
		double nodeMax = mMaxDat[i] <= 0 ? grDat.mMax : mMaxDat[i] - grDat.dMag / 2.0;
		double mfdMax = Math.max(grDat.mMax, nodeMax);
		
//		if (nodeMax <= grDat.mMax) {
		// mfdMax is either gridMax or some higher value
		double bVal = bGrid ? bDat[i] : grDat.bVal;
		GR_Data grNode = GR_Data.createForGridExport(aDat[i], bVal, grDat.mMin,
			mfdMax, grDat.dMag);
		GutenbergRichterMFD mfd = MFDs.newGutenbergRichterMoBalancedMFD(
			grNode.mMin, grNode.dMag, grNode.nMag, grNode.bVal, 1.0);
		mfd.scaleToIncrRate(grNode.mMin, MFDs.incrRate(grNode.aVal, 
			grNode.bVal, grNode.mMin));
		if (cutoffMax <= mfdMax) mfd.zeroAboveMag2(cutoffMax);
		wusScaleRates(mfd, i);
		// if node mMax <= gridMax add rates for defualt mags as atts
		node.setAttribute(RATES.toString(), Parsing.toString(mfd.yValues(), "%.8g"));
		// if node mMax > gridMax add mags as atts as well
		if (mfdMax > grDat.mMax) {
			node.setAttribute(MAGS.toString(), Parsing.toString(mfd.xValues(), "%.2f"));
		}
	}

	private void wusScaleRates(IncrementalMFD mfd, int idx) {
		for (int i = 0; i < mfd.getNum(); i++) {
			if (mfd.getX(i) > mTaper) mfd.set(i, mfd.getY(i) * wgtDat[idx]);
		}
	}
	
	/////////////// CEUS Customizations ///////////////
	
	private void addCEUS_MFD(int i, Element node) {
	
		// use fixed value if mMax matrix value was 0
		// for the large CEUS sources, we're going to fix mMax at it's
		// highest possible value (in this case 7.35 for AB and 7.15 for J);
		// the AB mMax grids specify 7.45 but that bin is always zeroed out
		// by the craton-margin scale factors and would be skipped in any event
		// due to GR bin recentering
		
		double cutoffMax = mMaxDat[i] <= 0 ? grDat.mMax + grDat.dMag / 2. : mMaxDat[i];
		double nodeMax = mMaxDat[i] <= 0 ? grDat.mMax : mMaxDat[i] - grDat.dMag / 2.0;
		double mfdMax = name.contains(".AB.") ? abMax : jMax;
		
		GR_Data grNode = GR_Data.createForGridExport(aDat[i], bDat[i],
			grDat.mMin, mfdMax, grDat.dMag);
		GutenbergRichterMFD mfd = MFDs.newGutenbergRichterMoBalancedMFD(
			grNode.mMin, grNode.dMag, grNode.nMag, grNode.bVal, 1.0);
		// a-value is stored as log10(a)
		mfd.scaleToIncrRate(grNode.mMin, MFDs.incrRate(grNode.aVal, grNode.bVal, grNode.mMin));
		if (cutoffMax < mfdMax) mfd.zeroAboveMag2(cutoffMax);
		ceusScaleRates(mfd, i);
		node.setAttribute(RATES.toString(), Parsing.toString(mfd.yValues(), "%.8g"));
	}

	private static double jMax = 7.15;
	private static double abMax = 7.35;
	private static double[] jMags = DataUtils.buildSequence(5.05, jMax, 0.1, true); //{5.05, 5.15, 5.25, 5.35, 5.45, 5.55, 5.65, 5.75, 5.85, 5.95, 6.05, 6.15, 6.25, 6.35, 6.45, 6.55, 6.65, 6.75, 6.85, 6.95, 7.05, 7.15 };
	private static double[] abMags = DataUtils.buildSequence(5.05, abMax, 0.1, true); //{5.05, 5.15, 5.25, 5.35, 5.45, 5.55, 5.65, 5.75, 5.85, 5.95, 6.05, 6.15, 6.25, 6.35, 6.45, 6.55, 6.65, 6.75, 6.85, 6.95, 7.05, 7.15, 7.25, 7.35 };
	// wtmj_cra: full weight up to 6.55; Mmax=6.85 @ 0.2 wt
	// wtmj_ext: full weight up to 6.85; Mmax=7.15 @ 0.2 wt
	// wtmab_cra: full weight up to 6.75; Mmax=7.05 @ 0.2 wt
	// wtmab_ext: full weight up to 7.15; Mmax=7.35 @ 0.2 wt
	// NOTE the 7.45 bin was removed (all zeros) pmpowers 11/15/2103
	private static double[] wtmj_cra =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0 };
	private static double[] wtmj_ext =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.7, 0.2, 0.0, 0.0 };
	private static double[] wtmab_cra = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9, 0.7, 0.2, 0.0, 0.0, 0.0 };
	private static double[] wtmab_ext = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2 };
	private static boolean[] cratonFlags;
	private static boolean[] marginFlags;
	
	
	private void ceusScaleRates(IncrementalMFD mfd, int idx) {
		initMasks();
		boolean craFlag = cratonFlags[idx];
		boolean marFlag = marginFlags[idx];
		if ((craFlag | marFlag) == false) return;
		double[] weights = name.contains(".AB.") ? 
			(craFlag ? wtmab_cra : wtmab_ext) :
				(craFlag ? wtmj_cra : wtmj_ext);
		applyWeight(mfd, weights);
	}
	
	private void applyWeight(IncrementalMFD mfd, double[] weights) {
		for (int i=0; i<mfd.getNum(); i++) {
			double weight = weights[i];
			if (weight == 1.0) continue;
			mfd.set(i, mfd.getY(i) * weight);
		}
	}

	private void initMasks() {
		// this is only used for CEUS so we don't have to worry about having
		// the wrong dimensions set for these static fields
		if (cratonFlags == null) {
			URL craton = SourceManager_2008.getCEUSmask("craton");
			URL margin = SourceManager_2008.getCEUSmask("margin");
			int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
			int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
			cratonFlags = Utils.readBoolGrid(craton, nRows, nCols);
			marginFlags = Utils.readBoolGrid(margin, nRows, nCols);
		}
	}


}
