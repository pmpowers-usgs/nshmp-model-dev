package gov.usgs.earthquake.nshm.convert;

import static com.google.common.base.Preconditions.checkState;
import static org.opensha.eq.forecast.SourceAttribute.MAGS;
import static org.opensha.eq.forecast.SourceAttribute.NAME;
import static org.opensha.eq.forecast.SourceAttribute.RATES;
import static org.opensha.eq.forecast.SourceAttribute.TYPE;
import static org.opensha.eq.forecast.SourceAttribute.WEIGHT;
import static org.opensha.eq.forecast.SourceElement.GRID_SOURCE_SET;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST;
import static org.opensha.eq.forecast.SourceElement.MAG_FREQ_DIST_REF;
import static org.opensha.eq.forecast.SourceElement.NODE;
import static org.opensha.eq.forecast.SourceElement.NODES;
import static org.opensha.eq.forecast.SourceElement.SETTINGS;
import static org.opensha.mfd.MfdType.INCR;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;
import gov.usgs.earthquake.nshm.util.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
import org.opensha.geo.GriddedRegion;
import org.opensha.geo.Location;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class IndexedGridConverter {

	private static final String GRID_XML = "grid_sources.xml";
		
	// Using strings for gird mag MFD parsing because of printed rounding errors
	// in source files
	private static final double[] mags;
	private static final Set<String> magStrSet;
	
	static {
		mags = DataUtils.buildSequence(5.05, 7.85, 0.1, true);
		magStrSet = ImmutableSet.copyOf(FluentIterable
			.from(Doubles.asList(mags))
			.transform(Parsing.formatDoubleFunction("%.2f")).toList());
	}
			
	static void processGridFile(String solDirPath, String sol, String outDirPath, double weight) throws Exception {
		
		File outDir = new File(outDirPath + sol);
		outDir.mkdirs();
		File gridOut = new File(outDir, GRID_XML);

		ZipFile zip = new ZipFile(solDirPath + sol + ".zip");
		ZipEntry entry = zip.getEntry(GRID_XML);		
		InputStream gridIn = zip.getInputStream(entry);
		processGridFile(gridIn, gridOut, sol, weight);
		
		zip.close();
	}
	
	/*
	 * Converts and condenses a grid file that accompanies a UC3 branch average
	 * solution. These are indexed according to the RELM_GRIDDED region and can
	 * therefore be processed and output in order and maintin consistency with
	 * desired grid source output format.
	 */
	private static void processGridFile(InputStream in, File out, String id, double weight)
			throws ParserConfigurationException, SAXException,
			TransformerConfigurationException, TransformerException,
			IOException {
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document docIn = dBuilder.parse(in);
		docIn.getDocumentElement().normalize();
		
        GriddedRegion gr = Utils.RELM_Region(0.1);
        
        // file out
        Document docOut = dBuilder.newDocument();
        Element rootOut = docOut.createElement(GRID_SOURCE_SET.toString());
        docOut.appendChild(rootOut);
        addAttribute(NAME, id, rootOut);
        addAttribute(WEIGHT, weight, rootOut);
        Element settings = addElement(SETTINGS, rootOut);
        Element mfdRef = addElement(MAG_FREQ_DIST_REF, settings);
        Element mfd = addElement(MAG_FREQ_DIST, mfdRef);
        addAttribute(TYPE, INCR, mfd);
		addAttribute(MAGS, Parsing.toString(Doubles.asList(mags), "%.2f"), mfd);
        Element nodesOut = addElement(NODES, rootOut);
        
        // file in
        Element rootIn = docIn.getDocumentElement();
        Element nodeList = (Element) rootIn.getElementsByTagName("MFDNodeList").item(0);
        NodeList nodesIn = nodeList.getElementsByTagName("MFDNode");
        for (int i=0; i<nodesIn.getLength(); i++) {
        	Element node = (Element) nodesIn.item(i);
        	int nodeIndex = Integer.parseInt(node.getAttribute("index"));
        	Location loc = gr.locationForIndex(nodeIndex);
        	List<Double> rates = processGridNode(node);
        	// add to out
        	Element nodeOut = addElement(NODE, nodesOut);
        	nodeOut.setTextContent(Utils.locToString(loc));
			addAttribute(RATES, Parsing.toString(rates, "%.8g"), nodeOut);
        }
       
		TransformerFactory transFactory = TransformerFactory.newInstance();
		Transformer trans = transFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		DOMSource source = new DOMSource(docOut);
		StreamResult result = new StreamResult(out);
		trans.transform(source, result);
 	}
	
	private static List<Double> processGridNode(Element e) {
		NodeList unassocElem = e.getElementsByTagName("UnassociatedFD");
		checkState(unassocElem.getLength() <= 1);
		List<Double> unassocRates = null;
		if (unassocElem.getLength() == 1) {
			unassocRates = mfdRates((Element) unassocElem.item(0));
		}
			
		NodeList subSeisElem = e.getElementsByTagName("SubSeisMFD");
		checkState(subSeisElem.getLength() <= 1);
		List<Double> subSeisRates = null;
		if (subSeisElem.getLength() == 1) {
			subSeisRates = mfdRates((Element) subSeisElem.item(0));
		}
		
		checkState(unassocRates != null || subSeisRates != null);
		
		if (unassocRates == null) return subSeisRates;
		if (subSeisRates == null) return unassocRates;
		return DataUtils.add(unassocRates, subSeisRates);
	}
	
	private static List<Double> mfdRates(Element e) {
		NodeList points = ((Element) e.getElementsByTagName("Points").item(0))
			.getElementsByTagName("Point");
		List<Double> rates = Lists.newArrayList();
		for (int i = 0; i < points.getLength(); i++) {
			Element point = (Element) points.item(i);
			String mag = String.format("%.2f", Double.parseDouble(point.getAttribute("x")));
			if (magStrSet.contains(mag)) {
				rates.add(Double.parseDouble(point.getAttribute("y")));
			}
		}
		return rates;
	}
		
}
