package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_FAULT_WC94_LENGTH;
import static org.opensha2.eq.model.SourceAttribute.DEPTH;
import static org.opensha2.eq.model.SourceAttribute.DIP;
import static org.opensha2.eq.model.SourceAttribute.ID;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RAKE;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceAttribute.WIDTH;
import static org.opensha2.eq.model.SourceElement.CLUSTER;
import static org.opensha2.eq.model.SourceElement.CLUSTER_SOURCE_SET;
import static org.opensha2.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha2.eq.model.SourceElement.GEOMETRY;
import static org.opensha2.eq.model.SourceElement.SETTINGS;
import static org.opensha2.eq.model.SourceElement.SOURCE;
import static org.opensha2.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha2.eq.model.SourceElement.TRACE;
import static org.opensha2.util.Parsing.addAttribute;
import static org.opensha2.util.Parsing.addComment;
import static org.opensha2.util.Parsing.addElement;
import static org.opensha2.util.Parsing.splitToDoubleList;
import static org.opensha2.util.Parsing.stripComment;
import static org.opensha2.util.Parsing.Delimiter.SPACE;
import gov.usgs.earthquake.nshm.util.MFD_Type;
import gov.usgs.earthquake.nshm.util.SourceRegion;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opensha2.eq.fault.FocalMech;
import org.opensha2.eq.model.MagUncertainty;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.util.Parsing;
import org.opensha2.util.Parsing.Delimiter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

/*
 * Convert NSHMP cluster input files to XML.
 * 
 * @author Peter Powers
 */
class ClusterConverter {

	private Logger log;

	private ClusterConverter() {}

	static ClusterConverter create(Logger log) {
		ClusterConverter cc = new ClusterConverter();
		cc.log = checkNotNull(log);
		return cc;
	}

	void convert(SourceFile sf, String outDir, SourceManager srcMgr) {

		try {
			log.info("");
			log.info("Source file: " + sf.name + " " + sf.region + " " + sf.weight);
			Exporter export = new Exporter();
			export.name = sf.name;
			export.id = -1;
			export.displayName = cleanName(export.name);
			export.weight = sf.weight; // TODO need to get weight from lookup
										// arrays in SrcMgr
			export.region = sf.region; // mag scaling relationships are region
										// dependent

			Iterator<String> lines = sf.lineIterator();

			// skip irrelevant header data
			skipSiteData(lines);
			lines.next(); // rMax and discretization
			skipGMMs(lines);
			lines.next(); // distance sampling on fault and dMove

			// load magnitude uncertainty data
			export.magDat = readMagUncertainty(Parsing.toLineList(lines, 4));
			if (log.isLoggable(Level.INFO)) {
				log.info(export.magDat.toString());
			}

			Map<Integer, ClusterData> srcMap = Maps.newHashMap();

			while (lines.hasNext()) {

				String fltDat = lines.next();
				// For NMSZ NSHMP uses a group id to identify fault variants, in
				// this case 5 arrayed west to east, and a segment or section id
				// to identify north, central and southern cluster model faults
				int groupNum = Parsing.readInt(fltDat, 3);
				int sectionNum = Parsing.readInt(fltDat, 4);
				String sectionName = Parsing.splitToList(fltDat, Delimiter.SPACE).get(5);

				// collect data on source name line
				ClusterData cd = srcMap.get(groupNum);
				if (cd == null) {
					cd = new ClusterData();
					cd.name = createGroupName(sf.name, groupNum);
					cd.weight = srcMgr.getClusterWeight(sf.name, groupNum);
					cd.id = -1;
					srcMap.put(groupNum, cd);
				}

				SourceData sd = new SourceData();
				cd.sources.add(sd);
				sd.name = createSectionName(sf.name, sectionNum, sectionName);
				sd.id = -1;
				sd.focalMech = Utils.typeForID(Parsing.readInt(fltDat, 1));
				sd.nMag = Parsing.readInt(fltDat, 2);
				sd.mfds = Lists.newArrayList();

				List<String> mfdSrcDat = Parsing.toLineList(lines, sd.nMag);
				read_MFDs(sd, mfdSrcDat, export);
				readTrace(lines, sd);

				// append dip to name if normal (NSHMP 3dip)
				if (sd.focalMech == FocalMech.NORMAL) {
					sd.name += " " + ((int) sd.dip);
				}
				if (sd.mfds.size() == 0) {
					log.severe("Source with no mfds");
					System.exit(1);
				}
				if (export.map.containsKey(sd.name)) {
					log.warning("Name map already contains: " + sd.name);
					// there are strike slip faults with no geometric dip
					// variants nested within
					// files of mostly normal faults with dip variants; because
					// the dip is not
					// appended to the name of SS faults, the name repeats;
					// however the
					// LinkedListMultimap takescare of collecting the different
					// mfds and weights
					// TODO reduce/combine MFDs see nv.3dip.ch.xml Kane SPring
					// Wash
				}
				export.map.put(cd.name, cd);
			}

			// KLUDGY: this should be handled now that a Set of names is used
			// in FaultSourceData, however we want to be aware of potential
			// duplicates so we now log the addition of existing names to
			// the name set.
			// if (fName.contains("3dip")) cleanStrikeSlip(srcList);

			String S = File.separator;
			String outPath = outDir + sf.region + S + sf.type + S + export.displayName + ".xml";
			File outFile = new File(outPath);
			Files.createParentDirs(outFile);
			export.writeXML(new File(outPath));

		} catch (Exception e) {
			log.log(Level.SEVERE, "Fault parse error: exiting", e);
			System.exit(1);
		}
	}

	private MagUncertainty readMagUncertainty(List<String> src) {

		// epistemic
		double[] epiDeltas = Doubles.toArray(splitToDoubleList(src.get(1), SPACE));
		double[] epiWeights = Doubles.toArray(splitToDoubleList(src.get(2), SPACE));
		double epiCutoff = 6.5;

		// aleatory
		List<Double> aleatoryMagDat = splitToDoubleList(stripComment(src.get(3), '!'), SPACE);
		double aleatorySigmaTmp = aleatoryMagDat.get(0);
		boolean moBalance = aleatorySigmaTmp > 0.0;
		double aleaSigma = Math.abs(aleatorySigmaTmp);
		int aleaCount = aleatoryMagDat.get(1).intValue() * 2 + 1;
		double aleaCutoff = 6.5;

		return MagUncertainty.create(epiDeltas, epiWeights, epiCutoff, aleaSigma, aleaCount,
			moBalance, aleaCutoff);
	}

	private void initRefCH(Exporter export, double rate) {
		if (export.refCH == null) {
			export.refCH = CH_Data.create(0.0, rate, 1.0, false);

			// the only time single mags will float is if they are
			// coming from a GR conversion in a ch file; charactersitic
			// magnitude is smaller than mag scaling would predict
		}
	}

	private void read_MFDs(SourceData sd, List<String> lines, Exporter export) {

		// for 2008 NSHMP all cluster sources are entered as characteristic
		// and fill all the supplied geometries
		boolean floats = false;
		for (String line : lines) {
			double rate = Parsing.readDouble(line, 1);
			initRefCH(export, rate);

			CH_Data ch = CH_Data.create(Parsing.readDouble(line, 0), rate,
				Parsing.readDouble(line, 2), floats);
			sd.mfds.add(ch);
			log(sd, MFD_Type.CH, floats);

		}
	}

	private void log(SourceData fd, MFD_Type mfdType, boolean floats) {
		String mfdStr = Strings.padEnd(mfdType.name(), 5, ' ') + (floats ? "f " : "  ");
		log.info(mfdStr + fd.name);
	}

	private void readTrace(Iterator<String> it, SourceData fd) {
		readFaultGeom(it.next(), fd);

		int traceCount = Parsing.readInt(it.next(), 0);
		List<String> traceDat = Parsing.toLineList(it, traceCount);
		List<Location> locs = Lists.newArrayList();
		for (String ptDat : traceDat) {
			List<Double> latlon = splitToDoubleList(ptDat, SPACE);
			locs.add(Location.create(latlon.get(0), latlon.get(1), 0.0));
		}
		fd.locs = LocationList.create(locs);

		// catch negative dips; kludge in configs
		// used instead of reversing trace
		if (fd.dip < 0) {
			fd.dip = -fd.dip;
			fd.locs = LocationList.reverseOf(fd.locs);
		}
	}

	private static void readFaultGeom(String line, SourceData fd) {
		List<Double> fltDat = splitToDoubleList(line, SPACE);
		fd.dip = fltDat.get(0);
		fd.width = fltDat.get(1);
		fd.top = fltDat.get(2);
	}

	private static void skipSiteData(Iterator<String> it) {
		int numSta = Parsing.readInt(it.next(), 0); // grid of sites or station
													// list
		// skip num station lines or lat lon bounds (2 lines)
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		it.next(); // site data (Vs30) and Campbell basin depth
	}

	private static void skipGMMs(Iterator<String> it) {
		int nP = Parsing.readInt(it.next(), 0); // num periods
		for (int i = 0; i < nP; i++) {
			double epi = Parsing.readDouble(it.next(), 1); // period w/ gm epi.
															// flag
			if (epi > 0) Iterators.advance(it, 3);
			it.next(); // out file
			it.next(); // num ground motion values
			it.next(); // ground motion values
			int nAR = Parsing.readInt(it.next(), 0); // num atten. rel.
			Iterators.advance(it, nAR); // atten rel
		}
	}

	// TODO naming needs to be generalized if possible

	private static String createGroupName(String name, int grp) {
		if (name.startsWith("newmad")) {
			return "NMSZ: " +
				((grp == 1) ? "West" : (grp == 2) ? "Mid-West" : (grp == 3) ? "Center" : (grp == 4)
					? "Mid-East" : (grp == 5) ? "East" : "unknown") + " Model";
		} else if (name.equals("NMFS_RLME_clu.in")) {
			return "NMFS RLME " + grp;
		} else if (name.equals("wasatch_slc.cluster.in")) {
			return "Wasatch: " +
				((grp == 1) ? "50 Dip" : (grp == 2) ? "35 Dip" : (grp == 5) ? "65 Dip" : "unknown") +
				" Model";
		} else {
			throw new UnsupportedOperationException("Name not recognized: " + name);
		}
	}

	private static String createSectionName(String filename, int sec, String namehint) {
		if (filename.startsWith("newmad")) {
			return ((sec == 1) ? "North" : (sec == 2) ? "Center" : (sec == 3) ? "South" : "unknown") +
				" Section";
		} else if (filename.equals("NMFS_RLME_clu.in")) {
			return namehint + " Section";
		} else if (filename.equals("wasatch_slc.cluster.in")) {
			return ((sec == 1) ? "North Section" : (sec == 2) ? "Tear Fault" : (sec == 3)
				? "South Section" : "unknown");
		} else {
			throw new UnsupportedOperationException("Name not recognized: " + filename);
		}
	}

	private static String cleanName(String name) {
		if (name.startsWith("wasatch")) return "Wasatch";
		if (name.startsWith("newmad2014")) {
			String retPer = Parsing.splitToList(name, Delimiter.PERIOD).get(1);
			return "USGS New Madrid " + retPer + "-year";
		}
		if (name.startsWith("NMFS_RLME")) return "SSCn New Madrid";
		return name;
	}

	static class ClusterData {
		String name;
		double weight;
		int id;
		List<SourceData> sources = Lists.newArrayList();
	}

	/* Wrapper class for individual sources */
	static class SourceData {
		List<MFD_Data> mfds = Lists.newArrayList();
		FocalMech focalMech;
		int nMag;
		String name;
		int id;
		LocationList locs;
		double dip;
		double width;
		double top;
		double weight;

		boolean equals(SourceData in) {
			return focalMech == in.focalMech && name.equals(in.name) && locs.equals(in.locs) &&
				dip == in.dip && width == in.width && top == in.top;
		}

		@Override public String toString() {
			StringBuilder sb = new StringBuilder(name);
			sb.append(" mech=" + focalMech);
			sb.append(" dip=" + dip);
			sb.append(" width=" + width);
			sb.append(" top=" + top);
			sb.append(locs);
			return sb.toString();
		}
	}

	static class Exporter {

		String name; // original name
		String displayName;
		double weight = 1.0;
		int id;
		SourceRegion region = null;
		Map<String, ClusterData> map = Maps.newLinkedHashMap();
		MagUncertainty magDat;

		CH_Data refCH;

		public void writeXML(File out) throws ParserConfigurationException, TransformerException {

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			// root elements
			Document doc = docBuilder.newDocument();
			doc.setXmlStandalone(true);
			Element root = doc.createElement(CLUSTER_SOURCE_SET.toString());
			doc.appendChild(root);
			addAttribute(NAME, displayName, root);
			addAttribute(ID, id, root);
			addAttribute(WEIGHT, weight, root);
			Converter.addDisclaimer(root);
			addComment(" Original source file: " + name + " ", root);

			// reference MFDs and uncertainty
			Element settings = addElement(SETTINGS, root);
			Element mfdRef;
			if (refCH != null) {
				mfdRef = addElement(DEFAULT_MFDS, settings);
				refCH.appendTo(mfdRef, null);
			}
			magDat.appendTo(settings);

			// source properties
			Element propsElem = addElement(SOURCE_PROPERTIES, settings);
			addAttribute(RUPTURE_SCALING, NSHM_FAULT_WC94_LENGTH, propsElem);

			for (Entry<String, ClusterData> entry : map.entrySet()) {
				ClusterData cd = entry.getValue();

				Element cluster = addElement(CLUSTER, root);
				addAttribute(NAME, cd.name, cluster);
				addAttribute(ID, cd.id, cluster);
				addAttribute(WEIGHT, cd.weight, cluster);

				// consolidate MFDs at beginning of element
				for (SourceData sd : cd.sources) {
					Element source = addElement(SOURCE, cluster);
					addAttribute(NAME, sd.name, source);
					addAttribute(ID, sd.id, source);
					
					// MFDs
					for (MFD_Data mfdDat : sd.mfds) {
						mfdDat.appendTo(source, refCH);
					}

					Element geom = addElement(GEOMETRY, source);
					addAttribute(DIP, sd.dip, geom);
					addAttribute(WIDTH, sd.width, geom);
					addAttribute(RAKE, sd.focalMech.rake(), geom);
					addAttribute(DEPTH, sd.top, geom);
					Element trace = addElement(TRACE, geom);
					trace.setTextContent(sd.locs.toString());

				}
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
	}

}
