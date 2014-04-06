package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.*;
import static gov.usgs.earthquake.nshm.util.FaultCode.*;
import static org.opensha.eq.fault.FocalMech.*;
import static org.opensha.eq.forecast.SourceType.*;
import static gov.usgs.earthquake.nshm.util.RateType.*;
import static gov.usgs.earthquake.nshm.util.SourceRegion.*;
import static gov.usgs.earthquake.nshm.util.Utils.readGrid;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.eq.fault.FocalMech;
import org.opensha.geo.GriddedRegion;
//import org.opensha.commons.geo.Direction;
//import org.opensha.commons.geo.GriddedRegion;
import org.opensha.geo.Location;
import org.opensha.geo.LocationList;
import org.opensha.geo.Region;
import org.opensha.geo.Regions;
import org.opensha.mfd.MFDs;

import gov.usgs.earthquake.nshm.util.FaultCode;

import org.opensha.util.Parsing;

import gov.usgs.earthquake.nshm.util.RateType;
//import org.opensha.nshmp2.util.NSHMP_Utils;
//import org.opensha.nshmp2.util.RateType;
//import org.opensha.nshmp2.util.SourceIMR;
import gov.usgs.earthquake.nshm.util.SourceRegion;
import gov.usgs.earthquake.nshm.util.Utils;
//import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
//import org.opensha.sha.magdist.IncrementalMagFreqDist;


import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Ints;

/**
 * 2008 NSHMP grid source parser.
 */
public class GridConverter {

	// TODO clean up after dealing with CEUS
	// there are craton notes and things like 'ceusScaleRates' that need
	// consideration

	// TODO kill FualtCode
	private static Logger log;
	
	private GridConverter() {}
	
	static GridConverter create(Logger log) {
		GridConverter gc = new GridConverter();
		gc.log = checkNotNull(log);
		return gc;
	}

	// path to binary grid files in grid source input files
	private static final String SRC_DIR= "conf";
	private static final String GRD_DIR = "GR_DOS/";
	
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
	private double[] aDat, bDat, mMinDat, mMaxDat, wgtDat;

	// build grids using broad region but reduce to src location and mfd lists
	// and a border (Region) used by custom calculator to skip grid entirely
	private LocationList srcLocs;
//	private List<IncrementalMFD> mfdList;
	private Region border;
	
	// temp list of srcIndices used to create bounding region; list is also
	// referenced when applying craton/margin weighs to mfds
	private int[] srcIndices; // already sorted when built
	
	public static void convert(SourceFile sf, String dir) {
		
		try {
			log.info("Starting source: " + sf.name);
			GridSourceData srcDat = new GridSourceData();
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
				srcDat.chDat = CH_Data.create(srcDat.grDat.mMin, 0.0, 1.0);
			}
			
			// iflt, ibmat, maxMat, Mtaper
			// iflt = 0 -> no finite faults
			// iflt = 1 -> apply finite fault corrections for M>6 assuming random
			// strike
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
			srcDat.mMaxGrid = Parsing.readInt(grdDat, 2) > 0 ? true : false;
			srcDat.mTaper = Parsing.readDouble(grdDat, 3); // magnitude at which wtGrid is applied
			srcDat.weightGrid = srcDat.mTaper > 0 ? true : false;
	
			if (srcDat.bGrid) srcDat.bGridURL = readSourceURL(lines.next(), sf);
			if (srcDat.mMaxGrid) srcDat.mMaxGridURL = readSourceURL(lines.next(), sf);
			if (srcDat.weightGrid) srcDat.weightGridURL = readSourceURL(lines.next(), sf);
			srcDat.aGridURL = readSourceURL(lines.next(), sf);
	
			// read rate information if rateType is CUMULATIVE
			// it will require conversion to INCREMENTAL
			readRateInfo(lines.next(), srcDat);
	
			// read strike or rjb array
			if (srcDat.fltCode == FIXED) srcDat.strike = Parsing.readDouble(lines.next(), 0);
			
			// done reading; skip atten rel configs
	
			srcDat.region = Regions.createRectangularGridded(
				"NSHMP " + srcDat.name,
				Location.create(srcDat.minLat, srcDat.minLon),
				Location.create(srcDat.maxLat, srcDat.maxLon),
				srcDat.dLat, srcDat.dLon,
				GriddedRegion.ANCHOR_0_0);
			
			log.info(srcDat.toString());

			
			initDataGrids(srcDat);
	//		srcIMR = SourceIMR.imrForSource(GRIDDED, srcRegion, srcName, fltCode);
			
	//		GridERF erf = createGridSource();
	//		return erf;
			
			String S = File.separator;
			String outPath = dir + S + sf.region + S + sf.type + S + 
					sf.name.substring(0, sf.name.lastIndexOf('.')) + ".xml";
			File outFile = new File(outPath);
			Files.createParentDirs(outFile);
			srcDat.writeXML(new File(outPath));
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Grid parse error", e);
		}

	}
	
	
	/*
	 * This line is set up to configure a probability distribution of magnitude
	 * dependent rupture top depths. These are actually not used in favor of
	 * fixed values for M<6.5 and M>=6.5
	 */
	private static void readRuptureTop(String line, GridSourceData gsd) {
		List<Double> depthDat = Parsing.toDoubleList(line);
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

	private static void readMechWeights(String line, GridSourceData gsd) {
		List<Double> weights = Parsing.toDoubleList(line);
		Map<FocalMech, Double> map = Maps.newEnumMap(FocalMech.class);
		map.put(STRIKE_SLIP, weights.get(0));
		map.put(REVERSE, weights.get(1));
		map.put(NORMAL, weights.get(2));
		gsd.mechWtMap = map;
	}

	private static void readLookupArrayDat(String line, GridSourceData gsd) {
		List<Double> rDat = Parsing.toDoubleList(line);
		gsd.dR = rDat.get(0).intValue();
		gsd.rMax = rDat.get(1).intValue();
	}

	private static void readSourceLatRange(String line, GridSourceData gsd) {
		List<Double> latDat = Parsing.toDoubleList(line);
		gsd.minLat = latDat.get(0);
		gsd.maxLat = latDat.get(1);
		gsd.dLat = latDat.get(2);
	}

	private static void readSourceLonRange(String line, GridSourceData gsd) {
		List<Double> lonDat = Parsing.toDoubleList(line);
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

	private static void readRateInfo(String line, GridSourceData gsd) {
		List<Double> rateDat = Parsing.toDoubleList(line);
		gsd.timeSpan =  rateDat.get(0);
		gsd.rateType = (rateDat.get(1).intValue() == 0) ? 
			INCREMENTAL : CUMULATIVE;
	}


	private static void createGridSource(GridSourceData gsd) throws IOException {
		
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

//	private void initSrcRegion(GriddedRegion region) {
//		LocationList srcLocs = new LocationList();
//		int currIdx = srcIndices[0];
//		srcLocs.add(region.locationForIndex(currIdx));
//		Direction startDir = Direction.WEST;
//		Direction sweepDir = startDir.next();
//		while (sweepDir != startDir) {
//			int sweepIdx = region.move(currIdx, sweepDir);
//			int nextIdx = Arrays.binarySearch(srcIndices, sweepIdx);
//			if (nextIdx >= 0) {
//				Location nextLoc = region.locationForIndex(srcIndices[nextIdx]);
//				//System.out.println(aDat[srcIndices[nextIdx]] + " " + nextLoc);
//				if (nextLoc.equals(srcLocs.get(0))) break;
//				srcLocs.add(nextLoc);
//				currIdx = srcIndices[nextIdx];
//				startDir = sweepDir.opposite().next();
//				sweepDir = startDir.next();
//				continue;
//			}
//			sweepDir = sweepDir.next();
//		}
//		// KLUDGY san gorgonio hack; only 11 grid points whose outline (16 pts)
	// TODO want ot create outline that steps out half a cell from each valid node
//		// does not play nice with Region
//		if (srcLocs.size() == 16) {
//			for (int i=0; i < srcLocs.size(); i++) {
//				if (i==0 || i==8) continue;
//				double offset = (i > 8) ? 0.01 : -0.01;
//				Location ol = srcLocs.get(i);
//				Location nl = new Location(ol.getLatitude() + offset, ol.getLongitude());
//				
//				srcLocs.set(i, nl);
//			}
//		}
//		border = new Region(srcLocs, null);
//	}

	
//	private void generateMFDs(GridSourceData gsd, GriddedRegion region) {
////		mfdList = Lists.newArrayList();
////		srcLocs = new LocationList();
////		List<Integer> srcIndexList = Lists.newArrayList();
//
//		for (int i = 0; i < aDat.length; i++) {
//			if (aDat[i] == 0) {
//				continue;
//			}
//			// use fixed value if mMax matrix value was 0
//			double maxM = mMaxDat[i] <= 0 ? grSrc.mMax : mMaxDat[i];
//			// a-value is stored as log10(a)
//			GR_Data gr = new GR_Data(aDat[i], bDat[i], mMinDat[i], maxM,
//				grSrc.dMag);
//			GutenbergRichterMFD mfd = new GutenbergRichterMFD(
//				gr.mMin, gr.nMag, gr.dMag, 1.0, gr.bVal);
//			mfd.scaleToIncrRate(gr.mMin, incrRate(gr.aVal, gr.bVal, gr.mMin));
//			// apply weight
//			if (weightGrid && mfd.getMaxX() >= mTaper) {
//				int j = mfd.getXIndex(mTaper + grSrc.dMag / 2);
//				for (; j < mfd.getNum(); j++)
//					mfd.set(j, mfd.getY(j) * wgtDat[i]);
//			}
//			mfdList.add(mfd);
//			srcLocs.add(region.locationForIndex(i));
////			if (Locations.areSimilar(region.locationForIndex(i), 
////				NEHRP_TestCity.SEATTLE.location())) {
////				System.out.println("aVal: " + aDat[i]);
////			}
////			if (i==61222) {
////				System.out.println("aValMax: " + aDat[i]);
////				System.out.println(mfd);
////			}
//			srcIndexList.add(i);
//		}
//		srcIndices = Ints.toArray(srcIndexList);
////		System.out.println("max aVal: " + Doubles.max(aDat));
////		System.out.println("max aVal: " + Math.pow(10, Doubles.max(aDat)));
//	}

	private static void initDataGrids(GridSourceData gsd) throws IOException {
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

	private static double[] makeGrid(int size, double value) {
		double[] dat = new double[size];
		Arrays.fill(dat, value);
		return dat;
	}



//	/////////////// CEUS Customizations ///////////////
//
//	// wtmj_cra: full weight up to 6.55; Mmax=6.85 @ 0.2 wt
//	// wtmj_ext: full weight up to 6.85; Mmax=7.15 @ 0.2 wt
//	// wtmab_cra: full weight up to 6.75; Mmax=7.05 @ 0.2 wt
//	// wtmab_ext: full weight up to 7.15; Mmax=7.35 @ 0.2 wt
//	private static double[] wtmj_cra =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
//	private static double[] wtmj_ext =  { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.7, 0.2, 0.0, 0.0, 0.0 };
//	private static double[] wtmab_cra = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9, 0.7, 0.2, 0.0, 0.0, 0.0, 0.0 };
//	private static double[] wtmab_ext = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.7, 0.2, 0.0 };
//	private static boolean[] cratonFlags;
//	private static boolean[] marginFlags;
//	
//	
//	private void ceusScaleRates() {
//		initMasks();
//		
//		// set weights by file name
//		double[] craWt = wtmj_cra;
//		double[] marWt = wtmj_ext;
//		if (srcName.contains(".AB.")) {
//			craWt = wtmab_cra;
//			marWt = wtmab_ext;
//		}
//		double[] weights;
//		
//		// adjust mfds
//		for (int i=0; i<srcIndices.length; i++) {
//			IncrementalMFD mfd = mfdList.get(i);
//			if (mfd == null) continue;
//			int flagIdx = srcIndices[i];
//			boolean craFlag = cratonFlags[flagIdx];
//			boolean marFlag = marginFlags[flagIdx];
//			if ((craFlag | marFlag) == false) continue;
//			weights = craFlag ? craWt : marWt;
//			applyWeight(mfd, weights);
//		}
//	}
//	
//	private void applyWeight(IncrementalMFD mfd, double[] weights) {
//		for (int i=0; i<mfd.getNum(); i++) {
//			double weight = weights[i];
//			if (weight == 1.0) continue;
//			mfd.set(i, mfd.getY(i) * weight);
//		}
//	}
//
//	private void initMasks() {
//		// this is only used for CEUS so we don't have to worry about having
//		// the wrong dimensions set for these static fields
//		if (cratonFlags == null) {
//			URL craton = Utils.getResource("/imr/craton");
//			URL margin = Utils.getResource("/imr/margin");
//			int nRows = (int) Math.rint((maxLat - minLat) / dLat) + 1;
//			int nCols = (int) Math.rint((maxLon - minLon) / dLon) + 1;
//			cratonFlags = NSHMP_Utils.readBoolGrid(craton, nRows, nCols);
//			marginFlags = NSHMP_Utils.readBoolGrid(margin, nRows, nCols);
//		}
//	}

}
