package gov.usgs.earthquake.nshm.convert;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;

import org.opensha.eq.forecast.SourceType;

import gov.usgs.earthquake.nshm.util.SourceRegion;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;

/*
 * Wrapper for NSHMP source files. These are typically *.in files that are
 * structured to be used with Fortran codes (e.g. hazgrd* and hazFX*).
 */
class SourceFile {

	SourceRegion region;
	SourceType type;
	URL url;
	double weight;
	String name;

	SourceFile(URL url, SourceRegion region, SourceType type, double weight) {
		this.url = url;
		this.region = region;
		this.type = type;
		this.weight = weight;
		String urlStr = url.toString();
		name = urlStr.substring(urlStr.lastIndexOf('/') + 1);
	}

	@Override
	public String toString() {
		return new StringBuffer(Strings.padEnd(region.toString(), 24, ' '))
			.append(Strings.padEnd(type.toString(), 12, ' '))
			.append(Strings.padEnd(String.format("%.7f", weight), 11, ' '))
			.append(name).toString();
	}
	
	/*
	 * Returns a line Iterable that ignores trailing comments
	 * that start with '!'.
	 */
	Iterator<String> lineIterator() throws IOException {
		return Iterables.transform(
			Resources.readLines(url, Charsets.US_ASCII),
			CommentStripper.INSTANCE).iterator();
	}
	
	private enum CommentStripper implements Function<String, String> {
		INSTANCE;
		@Override public String apply(String s) {
			int idx = s.indexOf('!');
			return idx != -1 ? s.substring(0, idx) : s;
		}
	}

}
