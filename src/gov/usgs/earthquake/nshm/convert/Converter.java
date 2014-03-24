package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshm.util.SourceRegion.*;
import static org.opensha.eq.forecast.SourceType.*;

import java.util.Arrays;
import java.util.List;

import org.opensha.data.DataUtils;

import com.google.common.collect.Lists;

/**
 * Starting point for conversions from NHSMP *.in files to XML.
 * @author Peter Powers
 */
class Converter {

	static SourceManager mgr2008 = SourceManager_2008.instance();
	static SourceManager mgr2014 = SourceManager_2014.instance();
	
	public static void main(String[] args) {
		convert2008();
//		scratch();
	}
	
	
	static void convert2014() {
		List<SourceFile> files = mgr2014.get(CASC, SUBDUCTION);
		String out = "tmp/NSHMP14-noRedux";
		convert(files, out);
	}

	static void convert2008() {
//		List<SourceFile> files = Lists.newArrayList();
//		files.addAll(SourceManager_2008.get(WUS, GRIDDED));
//		files.addAll(SourceManager_2008.get(CA, GRIDDED, "CAmap.21.ch.in"));
//		files.addAll(SourceManager_2008.get(CEUS, GRIDDED, "CEUS.2007all8.AB.in"));
//		String out = "tmp/NSHMP08-noRedux";
//		for (SourceFile sf : files) {
//			GridConverter.convert(sf, out);
//		}
		
//		List<SourceFile> files = SourceManager_2008.get(CA, FAULT); 
//		List<SourceFile> files = SourceManager_2008.get(null, null, "bFault.gr.in");
//		List<SourceFile> files = SourceManager_2008.get(null, null, "brange.3dip.gr.in");
		
		List<SourceFile> files = mgr2008.get(WUS, FAULT);
//		List<SourceFile> files = mgr2008.get(CASC, SUBDUCTION);
		String out = "tmp/NSHMP08-noRedux";
		convert(files, out);
	}
	
	static void convert(List<SourceFile> files, String out) {
		for (SourceFile file : files) {
			convert(file, out);
		}
	}
	
	static void convert(SourceFile file, String out) {
		switch(file.type) {
			case FAULT:
				FaultConverter.convert(file, out);
				break;
			case GRID:
				GridConverter.convert(file, out);
				break;
			case SUBDUCTION:
				SubductionConverter.convert(file, out);
		}
	}
	
	static void scratch() {
		double[] mags = {5.05,5.15,5.25,5.35,5.45,5.55,5.65,5.75,5.85,5.95,6.05,6.15,6.25,6.35,6.45,6.55,6.65,6.75,6.85,6.95,7.05,7.15,7.25,7.35,7.45,7.55,7.65,7.75,7.85};
		double[] rates = {9.2188683e-05,7.3228074e-05,5.8166789e-05,4.6203361e-05,3.6700951e-05,2.9152183e-05,2.3156355e-05,1.8393313e-05,1.4608420e-05,1.1750812e-05,7.0991947e-06,5.3194447e-06,5.3612616e-06,4.6009121e-06,3.6446066e-06,2.7082121e-06,1.9710816e-06,1.4143795e-06,1.0023155e-06,6.9170204e-07,4.5338399e-07,2.8427653e-07,1.7464344e-07,9.0721167e-08,3.7465800e-08,1.0269086e-08,3.6138641e-09,1.6483744e-09,1.5320036e-09};
		System.out.println(mags.length);
		System.out.println(rates.length);
	}
	
//	sources.addAll(SourceManager_2008.get(CEUS, GRIDDED, "CEUS.2007all8.AB.in"));
//	sources.addAll(SourceFileMgr.get(CA, GRIDDED, "mojave.in"));
//	sources.addAll(SourceFileMgr.get(CA, GRIDDED, "sangorg.in"));
//	sources.addAll(SourceFileMgr.get(CEUS, GRIDDED, "CEUSchar.71.in"));
//	sources.addAll(SourceFileMgr.get(WUS, GRIDDED, "EXTmap.ch.in"));
//	sources.addAll(SourceFileMgr.get(WUS, GRIDDED, "pnwdeep.in"));

	static void convert2008_MFDredux() {
		
	}
}
