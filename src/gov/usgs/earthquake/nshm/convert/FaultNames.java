package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkArgument;

import org.opensha2.internal.Parsing;
import org.opensha2.internal.Parsing.Delimiter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Iterables;

/**
 * Stores lookup maps for source fault names and IDs
 */
class FaultNames {

	private static final Path RESOURCE_PATH = Paths.get("src", "resources", "faults");

	private Map<String, Integer> nameMap;
	private Map<String, Integer> faultIdMap;

	private FaultNames(int year) {
		Path dataPath = null;
		switch (year) {
			case 2008:
				dataPath = RESOURCE_PATH.resolve("2008_Source_Param_May_31_2015.txt");
				break;
			case 2014:
				dataPath = RESOURCE_PATH.resolve("2014_Source_Param_May_31_2015.txt");
				break;
			default:
				throw new IllegalArgumentException("Unsupported year: " + year);
		}
		try {
			initMaps(dataPath);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void initMaps(Path path) throws IOException {
		nameMap = new HashMap<>();
		faultIdMap = new HashMap<>();
		List<String> lines = Files.readAllLines(path, StandardCharsets.US_ASCII);
		for (String line : Iterables.skip(lines, 1)) {
			
			int commaIndex = line.indexOf(',');
			int index = Integer.valueOf(line.substring(0, commaIndex));
			
			line = line.substring(commaIndex + 2);
			int quoteIndex = line.indexOf('"');
			String faultId = line.substring(0, quoteIndex);
			
			line = line.substring(quoteIndex + 2);
			String name = Parsing.trimEnds(line);
			
//			System.out.println(index + " - " + faultId + " - " + name);

			nameMap.put(name, index);
			faultIdMap.put(faultId, index);
		}
	}

	static FaultNames create(int year) {
		return new FaultNames(year);
	}

	int indexForName(String name) {
		checkArgument(nameMap.containsKey(name), "Missing fault name: %s", name);
		return nameMap.get(name);
	}
	
	int indexForFaultID(String id) {
		checkArgument(nameMap.containsKey(id), "Missing fault id: %s", id);
		return faultIdMap.get(id);
	}

	public static void main(String[] args) {
		FaultNames fn = create(2014);
	}

}
