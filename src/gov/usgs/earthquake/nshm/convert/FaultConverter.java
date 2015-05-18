package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.StandardSystemProperty.LINE_SEPARATOR;
import static gov.usgs.earthquake.nshm.util.SourceRegion.CEUS;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_FAULT_CA_ELLB_WC94_AREA;
import static org.opensha2.eq.fault.surface.RuptureScaling.NSHM_FAULT_WC94_LENGTH;
import static org.opensha2.eq.fault.surface.RuptureFloating.NSHM;
import static org.opensha2.eq.fault.surface.RuptureFloating.STRIKE_ONLY;
import static org.opensha2.eq.model.SourceAttribute.DEPTH;
import static org.opensha2.eq.model.SourceAttribute.DIP;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_FLOATING;
import static org.opensha2.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha2.eq.model.SourceAttribute.SURFACE_SPACING;
import static org.opensha2.eq.model.SourceAttribute.NAME;
import static org.opensha2.eq.model.SourceAttribute.RAKE;
import static org.opensha2.eq.model.SourceAttribute.WEIGHT;
import static org.opensha2.eq.model.SourceAttribute.WIDTH;
import static org.opensha2.eq.model.SourceElement.FAULT_SOURCE_SET;
import static org.opensha2.eq.model.SourceElement.GEOMETRY;
import static org.opensha2.eq.model.SourceElement.DEFAULT_MFDS;
import static org.opensha2.eq.model.SourceElement.SETTINGS;
import static org.opensha2.eq.model.SourceElement.SOURCE;
import static org.opensha2.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha2.eq.model.SourceElement.TRACE;
import static org.opensha2.util.Parsing.addAttribute;
import static org.opensha2.util.Parsing.addComment;
import static org.opensha2.util.Parsing.addElement;
import static org.opensha2.util.Parsing.splitToDoubleList;
import static org.opensha2.util.Parsing.splitToList;
import static org.opensha2.util.Parsing.stripComment;
import static org.opensha2.util.Parsing.Delimiter.SPACE;
import gov.usgs.earthquake.nshm.util.MFD_Type;
import gov.usgs.earthquake.nshm.util.SourceRegion;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
import org.opensha2.eq.fault.surface.RuptureFloating;
import org.opensha2.eq.fault.surface.RuptureScaling;
import org.opensha2.geo.Location;
import org.opensha2.geo.LocationList;
import org.opensha2.mfd.Mfds;
import org.opensha2.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

/*
 * Convert NSHMP fault input files to XML.
 * @author Peter Powers
 */
class FaultConverter {

	private Logger log;
	private FaultConverter() {}
	
	static FaultConverter create(Logger log) {
		FaultConverter fc = new FaultConverter();
		fc.log = checkNotNull(log);
		return fc;
	}
	
	void convert(SourceFile sf, String outDir) {
				
		try {
			log.info("");
			log.info("Source file: " + sf.name + " " + sf.region + " " + sf.weight);
			Exporter export = new Exporter();
			export.name = sf.name;
			export.displayName = cleanFileName(export.name);
			export.weight = sf.weight;
			export.region = sf.region; // mag scaling relationships are region dependent
	
			// KLUDGY nameIdx indicates the array index at which a fault
			// name begins; most NSHMP files define the fault name on a line such as:
			//
			//  2 3 1 1    805 Juniper Mountain fault
			//
			// where the starting index would be 4. (The identifying number is
			// necessary to distinguish some faults, e.g. Seattle Fault in orwa_c.in)
			// CA files generally start at idx=3; general WUS case is 4
			//
			// int nameIdx = (sf.region == CA || sf.region == CEUS) ? 3 :
			// now test CA by name as region has been moved to WUS
			int nameIdx = (isCA(sf.name) || sf.region == CEUS)
				? 3 : (sf.name.equals("wasatch.3dip.74.in")) ? 4 : 5;
	
			Iterator<String> lines = sf.lineIterator();
	
			// skip irrelevant header data
			skipSiteData(lines);
			lines.next(); // rMax and discretization
			skipGMMs(lines);
			lines.next(); // distance sampling on fault and dMove

			// load magnitude uncertainty data
			List<String> uncLines = Parsing.toLineList(lines, 4);
			export.magDat = readMagUncertainty(uncLines);
			export.hasAleatory = hasAleatory(uncLines);
			if (log.isLoggable(Level.INFO)) {
				log.info(export.magDat.toString());
			}
	
			while (lines.hasNext()) {
				
				// collect data on source name line
				SourceData fDat = new SourceData();
				List<String> srcInfo = splitToList(lines.next(), SPACE);
				MFD_Type mfdType = MFD_Type.typeForID(Integer.valueOf(srcInfo.get(0)));
				fDat.file = sf;
				fDat.focalMech = Utils.typeForID(Integer.valueOf(srcInfo.get(1)));
				fDat.nMag = Integer.valueOf(srcInfo.get(2));
				fDat.name = cleanName(Joiner.on(' ').join(
					Iterables.skip(srcInfo, nameIdx)));
	
				// read source magnitude data and build mfds; due to peculiarities
				// of how mfd's are handled with different uncertainty settings
				// under certain conditions (e.g. when mMax < 6.5), cloned magDat
				// are used so that any changes to magDat do not percolate to
				// other sources TODO these peculiarities are handled when sources
				// are reconstituted so this cloning probably isn't necessary
				List<String> mfdDat = Parsing.toLineList(lines, fDat.nMag);
				
				// starting with 2014 this may return a source with no mfds (due
				// to weights being set to 0; Zeng and Bird low-weight offshore
				// faults). Exception is caught below, a warning logged, and
				// iteration continues.
				read_MFDs(fDat, mfdType, mfdDat, export);
				
				readTrace(lines, fDat);
				
				// append dip to name if normal (NSHMP 3dip) 
				if (fDat.focalMech == FocalMech.NORMAL) {
					fDat.name += " " + ((int) fDat.dip);
				}
				if (fDat.mfds.size() == 0) {
					log.warning("Dropping source with no MFDs: " + fDat.name);
					continue;
				}
				if (export.map.containsKey(fDat.name)) {
					log.warning("Name map already contains: " + fDat.name);
					// there are strike slip faults with no geometric dip variants nested within
					// files of mostly normal faults with dip variants; because the dip is not
					// appended to the name of SS faults, the name repeats; however the
					// LinkedListMultimap takescare of collecting the different mfds and weights
					// TODO reduce/combine MFDs see nv.3dip.ch.xml Kane SPring Wash
				}
				export.map.put(fDat.name, fDat);
			}
			
			// KLUDGY: this should be handled now that a Set of names is used
			// in FaultSourceData, however we want to be aware of potential
			// duplicates so we now log the addition of existing names to 
			// the name set.
			// if (fName.contains("3dip")) cleanStrikeSlip(srcList);
			
			String S = File.separator;

			/*
			 * unsegmented aFaults use full down dip width floaters and
			 * therefore require config override
			 */
			String caSubDir = export.displayName.equals("aFault_unseg") ? "CA Custom" + S : "";
			
			String outPath = outDir + sf.region + S + sf.type + S + caSubDir + export.displayName +
				".xml";
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
		
		return MagUncertainty.create(epiDeltas, epiWeights, epiCutoff,
			aleaSigma, aleaCount, moBalance, aleaCutoff);
	}
	
	private boolean hasAleatory(List<String> src) {
		
		// this is a little redundant wrt readMagUncertainty(List<String> src),
		// but we'd rather keep everything private in MagUncertainty class 
		// rather than add methods just for parsing.
		
		// aleatory
		List<Double> aleatoryMagDat = splitToDoubleList(stripComment(src.get(3), '!'), SPACE);
		double aleaSigma = Math.abs(aleatoryMagDat.get(0));
		int aleaCount = aleatoryMagDat.get(1).intValue() * 2 + 1;

		return aleaCount > 1 && aleaSigma != 0.0;
	}


	
	private void read_MFDs(SourceData fd, MFD_Type type, 
			List<String> lines, Exporter export) {
		switch (type) {
			case CH:
				read_CH(lines, fd, export);
				break;
			case GR:
				read_GR(lines, fd, export);
				break;
			case GRB0:
				read_GRB0(lines, fd, export);
				break;
		}
	}

	private void read_CH(List<String> lines, SourceData fd, Exporter export) {
		initRefCH(export);
		
		boolean floats = false;
		for (String line : lines) {
			CH_Data ch = CH_Data.create(
				Parsing.readDouble(line, 0),
				Parsing.readDouble(line, 1),
				Parsing.readDouble(line, 2),
				floats);
			
			// added 2014 filter for 0 weight MFDs
			if (ch.weight == 0.0) {
				log.warning("Source MFD with zero weight: " + fd.name);
				continue;
			}
			
			fd.mfds.add(ch);
			log(fd, MFD_Type.CH, floats);
		}
	}
	
	private void initRefCH(Exporter export) {
		if (export.refCH != null) return;
		export.refCH = CH_Data.create(6.5, 0.0, 1.0, false);
		// the only time single mags will float is if they are
		// coming from a GR conversion in a ch file; charactersitic
		// magnitude is smaller than mag scaling would predict
	}
	
	private static boolean isCA(String name) {
		return name.startsWith("aFault") || name.startsWith("bFault");
	}
	
	private static RuptureScaling getScalingModel(String name) {
		return isCA(name) ? NSHM_FAULT_CA_ELLB_WC94_AREA : NSHM_FAULT_WC94_LENGTH;
	}
		
	private void initRefGR(Exporter export) {
		if (export.refGR != null) return;
		export.refGR = GR_Data.create(0.0, 0.8, 6.55, 7.5, 0.1, 1.0);
	}

	private void read_GR(List<String> lines, SourceData fd, Exporter export) {
		
		List<GR_Data> grData = new ArrayList<GR_Data>();
		for (String line : lines) {
			GR_Data gr = GR_Data.createForFault(line, fd, log);

			// added 2014 filter for 0 weight MFDs
			if (gr.weight == 0.0) {
				log.warning("Source MFD with zero weight: " + fd.name);
				continue;
			}
			
			grData.add(gr);
		}

		// build all GR_Data
		for (GR_Data gr : grData) {
			if (gr.nMag > 1) {
				initRefGR(export);

				fd.mfds.add(gr);
				log(fd, MFD_Type.GR, true);
			} else {
				initRefCH(export);
				
				CH_Data ch = CH_Data.create(
					gr.mMin, 
					Mfds.grRate(gr.aVal, gr.bVal, gr.mMin),
					gr.weight,
					true);
				fd.mfds.add(ch);
				log(fd, MFD_Type.CH, true);
			}
		}
	}

	private void read_GRB0(List<String> lines, SourceData fd, Exporter export) {
		
		checkArgument(!export.hasAleatory,
			"Aleatory unc. is incompatible with GR b=0 branches");

		List<GR_Data> grData = new ArrayList<GR_Data>();
		for (String line : lines) {
			GR_Data gr = GR_Data.createForFault(line, fd, log);
			checkArgument(gr.mMax > gr.mMin, "GR b=0 branch can't handle floating CH (mMin=mMax)");
			grData.add(gr);
		}

		for (GR_Data gr : grData) {
			initRefGR(export);
			
			gr.weight *= 0.5;
			fd.mfds.add(gr);
			log(fd, MFD_Type.GR, true);
			
			// adjust for b=0, preserving cumulative moment rate
			double tmr = Utils.totalMoRate(gr.mMin, gr.nMag, gr.dMag, gr.aVal, gr.bVal);
			double tsm = Utils.totalMoRate(gr.mMin, gr.nMag, gr.dMag, 0, 0);
			GR_Data grB0 = GR_Data.create(
				Math.log10(tmr / tsm),
				0.0,
				gr.mMin,
				gr.mMax,
				gr.dMag,
				gr.weight);
			fd.mfds.add(grB0);
			log(fd, MFD_Type.GRB0, true);
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
		int numSta = Parsing.readInt(it.next(), 0); // grid of sites or station list
		// skip num station lines or lat lon bounds (2 lines)
		Iterators.advance(it, (numSta > 0) ? numSta : 2);
		it.next(); // site data (Vs30) and Campbell basin depth
	}
	
	private static void skipGMMs(Iterator<String> it) {
		int nP = Parsing.readInt(it.next(), 0); // num periods
		for (int i = 0; i < nP; i++) {
			double epi = Parsing.readDouble(it.next(), 1); // period w/ gm epi. flag
			if (epi > 0) Iterators.advance(it, 3); 
			it.next(); // out file
			it.next(); // num ground motion values
			it.next(); // ground motion values
			int nAR = Parsing.readInt(it.next(), 0); // num atten. rel.
			Iterators.advance(it, nAR); // atten rel
		}
	}
	
	private static String cleanName(String name) {
		return CharMatcher.WHITESPACE.collapseFrom(name
			.replace("faults", "")
			.replace("fault", "")
			.replace("zone", "")
			.replace("-", " - ")
			.replace("/", " - ")
			.replace(" , ", " - ")
			.replace(", ", " - ")
			.replace(";", " : "),
				 ' ').trim();
	}
	
	private static String cleanFileName(String name) {
		
		// CEUS conversions - 2014 only for now
		if (name.contains("2014")) {
			if (name.startsWith("CEUScm")) {
				if (name.contains("meers")) return "SSCn Meers Full Rupture";
				if (name.contains("recur")) return "SSCn Cheraw Recurrence Model";
				if (name.contains("srchar")) return "SSCn Cheraw Full Rupture";
				if (name.contains("srgr")) return "SSCn Cheraw Partial Rupture";
				return "USGS Cheraw Meers";
			}
			if (name.startsWith("NMSZnocl")) {
				int p1 = name.indexOf('.');
				int p2 = name.indexOf('.', p1 + 1);
				return "USGS New Madrid " + name.substring(p1 + 1, p2) + "-year";
			}
			
		} else {
			// only exception to 2014 being in original source file name
			if (name.startsWith("NMFS_RFT.RLME")) return "SSCn Reelfoot";
		}
		
		// WUS conversions
		if (name.startsWith("wasatch_slc")) return "Wasatch";
		if (name.startsWith("2014WUS")) {
			String rupType = (name.contains(".char.")) ? "Full Rupture" : (name.contains(".gr.")) ?
				"Partial Rupture" : "Small Mag";
			String cleanedName = (name.contains("zeng")) ? "Zeng Model " + rupType : 
				(name.contains("bird")) ? "Bird Model " + rupType :
					"Geologic Model " + rupType;
			return cleanedName;
		}
		
		// most everything above applies to 2014, for 2008 just
		// strip the ".in" from the end of the file
		if (name.endsWith(".in")) return name.substring(0, name.length() - 3);
		
		return name;
	}
	
	/* Wrapper class for individual sources */
	static class SourceData {
		SourceFile file;
		List<MFD_Data> mfds = Lists.newArrayList();
		FocalMech focalMech;
		int nMag;
		String name;
		LocationList locs;
		double dip;
		double width;
		double top;
		
		boolean equals(SourceData in) {
			return file.name.equals(in.file.name) &&
					focalMech == in.focalMech &&
					name.equals(in.name) &&
					locs.equals(in.locs) &&
					dip == in.dip &&
					width == in.width &&
					top == in.top;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(name);
			sb.append(" mech=" + focalMech);
			sb.append(" dip=" + dip );
			sb.append(" width=" + width);
			sb.append(" top=" + top);
			sb.append(locs);
			return sb.toString();
		}
	}
	
	static class Exporter {
		
		String name;
		String displayName;
		double weight = 1.0;
		SourceRegion region = null;
		ListMultimap<String, FaultConverter.SourceData> map = LinkedListMultimap.create();
		MagUncertainty magDat;
		boolean hasAleatory;
		
		CH_Data refCH;
		GR_Data refGR;

		public void writeXML(File out) throws ParserConfigurationException,
				TransformerException {

			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

			// root elements
			Document doc = docBuilder.newDocument();
			doc.setXmlStandalone(true);
			Element root = doc.createElement(FAULT_SOURCE_SET.toString());
			doc.appendChild(root);
			addAttribute(NAME, displayName, root);
			addAttribute(WEIGHT, weight, root);
			addComment(" Original source file: " + name + " ", root);

			// reference MFDs and uncertainty
			Element settings = addElement(SETTINGS, root);
			Element mfdRef;
			if (refCH != null || refGR != null) {
				mfdRef = addElement(DEFAULT_MFDS, settings);
				if (refCH != null) refCH.appendTo(mfdRef, null);
				if (refGR != null) refGR.appendTo(mfdRef, null);
			}
			magDat.appendTo(settings);

			// source properties
			Element propsElem = addElement(SOURCE_PROPERTIES, settings);
			addAttribute(RUPTURE_SCALING, getScalingModel(name), propsElem);
			
			for (String name : map.keySet()) {
				
				Element src = addElement(SOURCE, root);
				addAttribute(NAME, name, src);

				List<FaultConverter.SourceData> fDatList = map.get(name);
				// first test that all non-MFD data of multi entries are equal
				if (fDatList.size() > 1) {
					FaultConverter.SourceData first = fDatList.get(0);

					for (int i = 1; i < fDatList.size(); i++) {
						if (!first.equals(fDatList.get(i))) {
							throw new IllegalStateException(
								LINE_SEPARATOR.value() + name + " in " + first.file.name +
									" has multiple dissimilar entries" + LINE_SEPARATOR.value() +
									"index = 0 :: " + first + LINE_SEPARATOR.value() +
									"index = " + i + " :: " + fDatList.get(i));
						}
					}
				}
				// consolidate MFDs at beginning of element
				for (FaultConverter.SourceData fDat : fDatList) {
					// MFDs
					for (MFD_Data mfdDat : fDat.mfds) {
						mfdDat.appendTo(src, (mfdDat instanceof CH_Data) ? refCH : refGR);
					}
				}
				// append geometry from first entry
				FaultConverter.SourceData first = fDatList.get(0);
				Element geom = addElement(GEOMETRY, src);
				addAttribute(DIP, first.dip, geom);
				addAttribute(WIDTH, first.width, geom);
				addAttribute(RAKE, first.focalMech.rake(), geom);
				addAttribute(DEPTH, first.top, geom);
				Element trace = addElement(TRACE, geom);
				trace.setTextContent(first.locs.toString());
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
