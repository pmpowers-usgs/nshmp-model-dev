package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;
import static gov.usgs.earthquake.nshm.util.SourceRegion.*;
import static org.opensha.eq.forecast.SourceType.*;
import gov.usgs.earthquake.nshm.util.SourceRegion;
import gov.usgs.earthquake.nshm.util.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opensha.eq.forecast.SourceType;

import com.google.common.base.StandardSystemProperty;

/*
 * Starting point for conversions from NHSMP *.in files to XML. To keep some order and preserve logs
 * by file type and region, the methods in this class process files in groups by type and region.
 * 
 * @author Peter Powers
 */
class Converter {

	private static final String S = StandardSystemProperty.FILE_SEPARATOR.value();
	private static final SourceManager MGR_2008 = SourceManager_2008.instance();
	private static final SourceManager MGR_2014 = SourceManager_2014.instance();
	private static final String FCAST_DIR = "forecasts" + S;
	
	private static final String LOG_DIR = FCAST_DIR + "logs" + S;
	private static final Level LEVEL = Level.INFO;
	
	public static void main(String[] args) {
		convert2008();
		convert2014();
	}
	
	static void convert2008() {
		List<SourceFile> files;
		
//		files = MGR_2008.get(WUS, FAULT);
//		convertFault(files, WUS, "2008");

//		files = MGR_2008.get(CA, FAULT);
//		convertFault(files, CA, "2008");

//		files = MGR_2008.get(CA, GRID);
//		convertGrid(files, CA, "2008");
		
//		files = MGR_2008.get(CASC, SUBDUCTION);
//		convertInterface(files, CA, "2008");
		
	}
	
	static void convert2014() {
		List<SourceFile> files;
		
		files = MGR_2014.get(CASC, SUBDUCTION);
		convertInterface(files, CASC, "2014");
	}

	static void convertGrid(List<SourceFile> files, SourceRegion region, 
			String yr) {
		String out = FCAST_DIR + yr + S;
		Logger logger = createLogger("grid-convert", region, yr);
		GridConverter converter = GridConverter.create(logger);
		for (SourceFile file : files) {
			checkArgument(file.type == GRID, "Wrong file type: %s", file.type.name());
			converter.convert(file, out);
		}
	}

	static void convertFault(List<SourceFile> files, SourceRegion region, 
			String yr) {
		String out = FCAST_DIR + yr + S;
		Logger logger = createLogger("fault-convert", region, yr);
		FaultConverter converter = FaultConverter.create(logger);
		for (SourceFile file : files) {
			checkArgument(file.type == FAULT, "Wrong file type: %s", file.type.name());
			converter.convert(file, out);
		}
	}
	
	static void convertInterface(List<SourceFile> files, SourceRegion region, 
			String yr) {
		String out = FCAST_DIR + yr + S;
		Logger logger = createLogger("interface-convert", region, yr);
		SubductionConverter converter = SubductionConverter.create(logger);
		for (SourceFile file : files) {
			checkArgument(file.type == SUBDUCTION, "Wrong file type: %s", file.type.name());
			converter.convert(file, out);
		}
	}

	static final SimpleDateFormat sdf = new SimpleDateFormat("[yy.MM.dd-HH.mm]");
	
	static Logger createLogger(String name, SourceRegion region, String yr) {
		String time = sdf.format(new Date());
		return Utils.logger(LOG_DIR + name + "-" + yr + "-" +
				region.name() + "-" + time + ".log", LEVEL);
	}
}
