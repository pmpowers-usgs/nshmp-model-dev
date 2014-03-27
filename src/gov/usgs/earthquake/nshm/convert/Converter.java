package gov.usgs.earthquake.nshm.convert;

import static gov.usgs.earthquake.nshm.util.SourceRegion.*;
import static org.opensha.eq.forecast.SourceType.*;

import java.util.List;

/*
 * Starting point for conversions from NHSMP *.in files to XML.
 * @author Peter Powers
 */
class Converter {

	static SourceManager mgr2008 = SourceManager_2008.instance();
	static SourceManager mgr2014 = SourceManager_2014.instance();
	
	public static void main(String[] args) {
		convert2008();
//		convert2014();
	}
	
	
	static void convert2008() {
		List<SourceFile> files = mgr2008.get(WUS, FAULT);
		String out = "tmp/2008";
		convert(files, out);
	}
	
	static void convert2014() {
		List<SourceFile> files = mgr2014.get(CASC, SUBDUCTION);
		String out = "tmp/2014";
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
	
}
