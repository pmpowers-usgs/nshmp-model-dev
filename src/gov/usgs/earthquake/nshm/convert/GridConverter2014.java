package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkNotNull;
import static gov.usgs.earthquake.nshm.util.FaultCode.FIXED;
import static gov.usgs.earthquake.nshm.util.RateType.CUMULATIVE;
import static gov.usgs.earthquake.nshm.util.RateType.INCREMENTAL;
import static gov.usgs.earthquake.nshm.util.Utils.readGrid;
import static org.opensha.eq.fault.FocalMech.NORMAL;
import static org.opensha.eq.fault.FocalMech.REVERSE;
import static org.opensha.eq.fault.FocalMech.STRIKE_SLIP;
import static org.opensha.util.Parsing.splitToDoubleList;
import static org.opensha.util.Parsing.Delimiter.SPACE;
import gov.usgs.earthquake.nshm.util.FaultCode;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.eq.fault.FocalMech;
import org.opensha.geo.GriddedRegion;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Region;
import org.opensha.geo.Regions;
import org.opensha.util.Parsing;
import org.opensha.util.Parsing.Delimiter;

import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import com.google.common.io.Files;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;

/*
 * Convert 2014 NSHMP grid input files to XML.
 * 
 * This class was required because 2014 CEUS-wide grid sources have novel
 * Mmax zonations that do not parse. The associated mMax file gives a zone
 * id that is then used to pick an mMax distribution listed in the input file.
 * This parser separates out each source by zone and sets up grid files
 * that define multiple default GR Mfds.
 * 
 * @author Peter Powers
 */
class GridConverter2014 {

	private Logger log;
	private GridConverter2014() {}
	
	static GridConverter2014 create(Logger log) {
		GridConverter2014 gc = new GridConverter2014();
		gc.log = checkNotNull(log);
		return gc;
	}

	// path to binary grid files in grid source input files
	private static final String SRC_DIR= "conf";
//	private static final String GRD_DIR = "GR_DOS/";
	
	// parsed
//	private String srcName;
//	private SourceRegion srcRegion;
//	private SourceIMR srcIMR;
//	private double srcWt;
//	private double minLat, maxLat, dLat;
//	private double minLon, maxLon, dLon;
//	private double[] depths;
//	private Map<FocalMech, Double> mechWtMap;
//	private double dR, rMax;
//	private GR_Data grSrc;
//	private FaultCode fltCode;
//	private boolean bGrid, mMaxGrid, weightGrid;
//	private double mTaper;
//	private URL aGridURL, bGridURL, mMaxGridURL, weightGridURL;
//	private double timeSpan;
//	private RateType rateType;
//	private double strike = Double.NaN;

	// generated
//	private double[] aDat, bDat, mMinDat, mMaxDat, wgtDat;

	// build grids using broad region but reduce to src location and mfd lists
	// and a border (Region) used by custom calculator to skip grid entirely
	private LocationList srcLocs;
//	private List<IncrementalMFD> mfdList;
	private Region border;
	
	// temp list of srcIndices used to create bounding region; list is also
	// referenced when applying craton/margin weighs to mfds
	private int[] srcIndices; // already sorted when built
	
	void convert(SourceFile sf, String dir) {
		
		try {
			log.info("Starting source: " + sf.name);
			GridSourceData2014 srcDat = new GridSourceData2014();
			srcDat.name = sf.name;
			srcDat.weight = sf.weight;
					
			Iterator<String> lines = sf.lineIterator();
	
			// grid of sites (1-30) or station list (0)
			int numSta = Parsing.readInt(lines.next(), 0);
			// skip stations or lat-lon bounds
			Iterators.advance(lines, (numSta > 0) ? numSta : 2);
			// skip site data (Vs30) and Campbell basin depth
			lines.next();
			// read rupture top data (num, [z, wt M<=6.5, wt M>6.5], ...)
			readRuptureTop(lines.next(), srcDat);
			srcDat.depthMag = 6.5;
			// read focal mech weights (SS, REVERSE, NORMAL)
			readMechWeights(lines.next(), srcDat);
			// read gm lookup array parameters; delta R and R max
			readLookupArrayDat(lines.next(), srcDat);
	
			// read source region dimensions
			readSourceLatRange(lines.next(), srcDat);
			readSourceLonRange(lines.next(), srcDat);
	
			// mag data - grids always supply GR data, however, Charleston fixed
			// strike grids are also fixed mag to we convert them to SINGLE here
			srcDat.grDat = GR_Data.createForGrid(lines.next());
			if (DoubleMath.fuzzyEquals(srcDat.grDat.mMin, srcDat.grDat.mMax, 0.000001)) {
				// if mMin == mMax, populate CH_Data field
				srcDat.chDat = CH_Data.create(srcDat.grDat.mMin, 0.0, 1.0, false);
			}
			
			// iflt, ibmat, maxMat, Mtaper
			// iflt = 0 -> no finite faults
			// iflt = 1 -> apply finite fault corrections for M>6 assuming random strike
			// iflt = 2 -> use finite line faults for M>6 and fix strike
			// iflt = 3 -> use finite faults with Johston mblg to Mw converter
			// iflt = 4 -> use finite faults with Boore and Atkinson mblg to Mw
			// converter
			// ibmax = 0 -> use specified b value
			// ibmax = 1 -> use b value matrix (provided in a file)
			// maxMat = 0 -> use specified maximum magnitude
			// maxMat = 1 -> use maximum magnitude matrix (provided in a file)
			// maxMat = -1 -> use as maximum magnitude the minimum between the
			// default and grid value
			String grdDat = lines.next();
			srcDat.fltCode = FaultCode.typeForID(Parsing.readInt(grdDat, 0));
			srcDat.bGrid = Parsing.readInt(grdDat, 1) > 0 ? true : false;
			int mMaxFlag = Parsing.readInt(grdDat, 2);
			srcDat.mMaxGrid = mMaxFlag > 0 ? true : false;
			srcDat.mTaper = Parsing.readDouble(grdDat, 3); // magnitude at which wtGrid is applied
			srcDat.weightGrid = srcDat.mTaper > 0 ? true : false;
			
			if (!srcDat.mMaxGrid) throw new IllegalStateException("must have mMax flag data");			
	
			if (srcDat.bGrid) srcDat.bGridURL = readSourceURL(lines.next(), sf);
			if (srcDat.mMaxGrid) srcDat.mMaxGridURL = readSourceURL(lines.next(), sf);
			if (srcDat.weightGrid) srcDat.weightGridURL = readSourceURL(lines.next(), sf);

			readMaxDistros(lines, mMaxFlag, srcDat);

			srcDat.aGridURL = readSourceURL(lines.next(), sf);
	
			// read rate information if rateType is CUMULATIVE
			// it will require conversion to INCREMENTAL
			readRateInfo(lines.next(), srcDat);
	
			// read strike or rjb array
			if (srcDat.fltCode == FIXED) {
				double strike = Parsing.readDouble(lines.next(), 0);
				if (strike < 0.0) strike += 360.0;
				srcDat.strike = strike;
			}
			
			// done reading; skip atten rel configs
	
			srcDat.region = Regions.createRectangularGridded(
				"NSHMP " + srcDat.name,
				Location.create(srcDat.minLat, srcDat.minLon),
				Location.create(srcDat.maxLat, srcDat.maxLon),
				srcDat.dLat, srcDat.dLon,
				GriddedRegion.ANCHOR_0_0);
			
			initDataGrids(srcDat);
			
			// now that grids are populated; consolidate zoe counts in a bag
			List<Double> mMaxValues = Doubles.asList(srcDat.mMaxDat);
			srcDat.mMaxZoneBag = TreeMultiset.create(mMaxValues);

			log.info(srcDat.toString());

			String S = File.separator;
			String outPath = dir + S + sf.region + S + sf.type + S;
			String srcName = sf.name.substring(0, sf.name.lastIndexOf('.'));
			String outNameBase = "ceus_" + (srcName.contains("2zone") ? "usgs_" : "sscn_") +
					(srcName.contains("adapt") ? "adapt_" : "fixed_");
			
			for (int i=0; i<srcDat.mMaxWtMaps.size(); i++) {
				// skip empty zones
				double mMaxZoneValue = new Integer(i + 1).doubleValue();
				if (srcDat.mMaxZoneBag.count(mMaxZoneValue) == 0) continue;

				String outName = outNameBase + "z" + (i + 1) + ".xml";
				srcDat.fName = outName;
				File outFile = new File(outPath, outName);
				Files.createParentDirs(outFile);
				srcDat.writeXML(outFile, i);
			}
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Grid parse error: exiting", e);
			System.exit(1);
		}

	}
	
	
	/*
	 * This line is set up to configure a probability distribution of magnitude
	 * dependent rupture top depths. These are actually not used in favor of
	 * fixed values for M<6.5 and M>=6.5
	 */
	private static void readRuptureTop(String line, GridSourceData2014 gsd) {
		List<Double> depthDat = splitToDoubleList(line, SPACE);
		int numDepths = depthDat.get(0).intValue();
		double loMagDepth, hiMagDepth;
		if (numDepths == 1) {
			loMagDepth = depthDat.get(1);
			hiMagDepth = depthDat.get(1);
		} else {
			loMagDepth = depthDat.get(4);
			hiMagDepth = depthDat.get(1);
		}
		gsd.depths = new double[] { loMagDepth, hiMagDepth };
	}

	private static void readMechWeights(String line, GridSourceData2014 gsd) {
		List<Double> weights = splitToDoubleList(line, SPACE);
		Map<FocalMech, Double> map = Maps.newEnumMap(FocalMech.class);
		map.put(STRIKE_SLIP, weights.get(0));
		map.put(REVERSE, weights.get(1));
		map.put(NORMAL, weights.get(2));
		gsd.mechWtMap = map;
	}

	private static void readLookupArrayDat(String line, GridSourceData2014 gsd) {
		List<Double> rDat = splitToDoubleList(line, SPACE);
		gsd.dR = rDat.get(0).intValue();
		gsd.rMax = rDat.get(1).intValue();
	}

	private static void readSourceLatRange(String line, GridSourceData2014 gsd) {
		List<Double> latDat = splitToDoubleList(line, SPACE);
		gsd.minLat = latDat.get(0);
		gsd.maxLat = latDat.get(1);
		gsd.dLat = latDat.get(2);
	}

	private static void readSourceLonRange(String line, GridSourceData2014 gsd) {
		List<Double> lonDat = splitToDoubleList(line, SPACE);
		gsd.minLon = lonDat.get(0);
		gsd.maxLon = lonDat.get(1);
		gsd.dLon = lonDat.get(2);
	}
	
	private static URL readSourceURL(String path, SourceFile sf)
			throws MalformedURLException {
		String srcURL = sf.url.toString();
		String grdURL = srcURL.substring(0, srcURL.lastIndexOf(SRC_DIR)) + 
				path.substring(3);
		return new URL(grdURL);
	}
	
	private static void readMaxDistros(Iterator<String> lines, int size, GridSourceData2014 gsd) {
		List<Map<Double, Double>> mMaxWtMaps = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			Map<Double, Double> mMaxWtMap = new TreeMap<>();
			String line = lines.next();
			List<Double> values = Parsing.splitToDoubleList(line, Delimiter.SPACE);
			for (int j = 2; j < values.size(); j += 2) {
				mMaxWtMap.put(values.get(j), values.get(j + 1));
			}
			mMaxWtMaps.add(mMaxWtMap);
		}
		gsd.mMaxWtMaps = mMaxWtMaps;
	}

	private static void readRateInfo(String line, GridSourceData2014 gsd) {
		List<Double> rateDat = splitToDoubleList(line, SPACE);
		gsd.timeSpan =  rateDat.get(0);
		gsd.rateType = (rateDat.get(1).intValue() == 0) ? 
			INCREMENTAL : CUMULATIVE;
	}


	private static void createGridSource(GridSourceData2014 gsd) throws IOException {
		
		initDataGrids(gsd);

		GriddedRegion region = Regions.createRectangularGridded(
			"NSHMP " + gsd.name,
			Location.create(gsd.minLat, gsd.minLon),
			Location.create(gsd.maxLat, gsd.maxLon),
			gsd.dLat, gsd.dLon,
			GriddedRegion.ANCHOR_0_0);
		
//		generateMFDs(region);
//		initSrcRegion(region);

//		// KLUDGY: need to post process CEUS grids to handle craton and
//		// extended margin weighting grids
//		if (srcName.contains("2007all8")) {
//			ceusScaleRates();
//		}
//
//		GridERF gs = new GridERF(srcName, generateInfo(), border,
//			srcLocs, mfdList, depths, mechWtMap, fltCode, strike, srcRegion,
//			srcIMR, srcWt, rMax, dR);
//		return gs;
	}


	private static void initDataGrids(GridSourceData2014 gsd) throws IOException {
		int nRows = (int) Math.rint((gsd.maxLat - gsd.minLat) / gsd.dLat) + 1;
		int nCols = (int) Math.rint((gsd.maxLon - gsd.minLon) / gsd.dLon) + 1;
		
		// always have an a-grid file
		gsd.aDat = readGrid(gsd.aGridURL, nRows, nCols);
		
		// might have a b-grid file, but not likely
		if (gsd.bGrid) {
			gsd.bDat = readGrid(gsd.bGridURL, nRows, nCols);
			// KLUDGY numerous b-values are 0 but there is a hook in
			// hazgridXnga5 (line 931) to override a grid based b=0 to the
			// b-value set in the config for a grid source.
			for (int i = 0; i < gsd.bDat.length; i++) {
				if (gsd.bDat[i] == 0.0) gsd.bDat[i] = gsd.grDat.bVal;
			}
		}

		// variable mMax is common
		if (gsd.mMaxGrid) {
			gsd.mMaxDat = readGrid(gsd.mMaxGridURL, nRows, nCols);
		}

		// weights; mostly for CA
		if (gsd.weightGrid) {
			gsd.wgtDat = readGrid(gsd.weightGridURL, nRows, nCols);
		}
	}

}
