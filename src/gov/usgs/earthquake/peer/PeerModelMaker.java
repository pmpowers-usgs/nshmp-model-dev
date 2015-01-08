package gov.usgs.earthquake.peer;

import static gov.usgs.earthquake.peer.PeerTests.*;
import static org.opensha.eq.model.SourceType.*;
import static org.opensha.eq.fault.surface.RuptureScaling.PEER;
import static org.opensha.eq.model.SourceAttribute.DEPTH;
import static org.opensha.eq.model.SourceAttribute.DIP;
import static org.opensha.eq.model.SourceAttribute.FLOATS;
import static org.opensha.eq.model.SourceAttribute.MAGS;
import static org.opensha.eq.model.SourceAttribute.MAG_DEPTH_MAP;
import static org.opensha.eq.model.SourceAttribute.RUPTURE_SCALING;
import static org.opensha.eq.model.SourceAttribute.NAME;
import static org.opensha.eq.model.SourceAttribute.RAKE;
import static org.opensha.eq.model.SourceAttribute.RATES;
import static org.opensha.eq.model.SourceAttribute.TYPE;
import static org.opensha.eq.model.SourceAttribute.WEIGHT;
import static org.opensha.eq.model.SourceAttribute.WIDTH;
import static org.opensha.eq.model.SourceElement.AREA_SOURCE_SET;
import static org.opensha.eq.model.SourceElement.FAULT_SOURCE_SET;
import static org.opensha.eq.model.SourceElement.INCREMENTAL_MFD;
import static org.opensha.eq.model.SourceElement.BORDER;
import static org.opensha.eq.model.SourceElement.GEOMETRY;
import static org.opensha.eq.model.SourceElement.SETTINGS;
import static org.opensha.eq.model.SourceElement.SOURCE;
import static org.opensha.eq.model.SourceElement.SOURCE_PROPERTIES;
import static org.opensha.eq.model.SourceElement.TRACE;
import static org.opensha.mfd.MfdType.INCR;
import static org.opensha.util.Parsing.addAttribute;
import static org.opensha.util.Parsing.addElement;
import gov.usgs.earthquake.model.GmmCreator;
import gov.usgs.earthquake.nshm.convert.CH_Data;
import gov.usgs.earthquake.nshm.convert.MFD_Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

import org.opensha.eq.model.SourceType;
import org.opensha.geo.Location;
import org.opensha.mfd.IncrementalMfd;
import org.opensha.util.Parsing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Add comments here
 *
 * @author Peter Powers
 */
public class PeerModelMaker {

	static final String SET1_CASE1 = "Set1-Case1";
	static final String SET1_CASE2 = "Set1-Case2";
	static final String SET1_CASE3 = "Set1-Case3";
	static final String SET1_CASE4 = "Set1-Case4";
	static final String SET1_CASE5 = "Set1-Case5";
	static final String SET1_CASE6 = "Set1-Case6";
	static final String SET1_CASE7 = "Set1-Case7";
	static final String SET1_CASE8A = "Set1-Case8a";
	static final String SET1_CASE8B = "Set1-Case8b";
	static final String SET1_CASE8C = "Set1-Case8c";
	static final String SET1_CASE10 = "Set1-Case10";
	static final String SET1_CASE11 = "Set1-Case11";

	static final String FAULT1_SOURCE = "PEER: Fault 1";
	static final String FAULT2_SOURCE = "PEER: Fault 2";
	static final String AREA1_SOURCE = "PEER: Area 1";

	static final String MODEL_DIR = "models/PEER";
	static final String SOURCE_FILE = "test.xml";
	static final String GMM_FILE = "gmm.xml";
	
	private final DocumentBuilder docBuilder;

	private PeerModelMaker() throws ParserConfigurationException {
		docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	}
	
	public static void main(String[] args) throws Exception {
		PeerModelMaker pmm = new PeerModelMaker();
		pmm.writeModels();
	}
	
	void writeModels() throws Exception {
		Path path = null;
		
		path = Paths.get(MODEL_DIR, SET1_CASE1, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE1, F1_SINGLE_6P5_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);
		
		path = Paths.get(MODEL_DIR, SET1_CASE2, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE2, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);
		
		// TODO needs to be able to handle ruptureScaling sigma
		path = Paths.get(MODEL_DIR, SET1_CASE3, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE3, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE4, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault2(SET1_CASE4, F2_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE5, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE5, F1_GR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE6, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE6, F1_GAUSS_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE7, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE7, F1_YC_CHAR_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);
		
		// TODO how to handle gmm sigma overrides; all cases above should be zero
		// cases below are sigma with various truncations
		path = Paths.get(MODEL_DIR, SET1_CASE8A, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE8A, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE8B, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE8B, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE8C, FAULT.toString());
		write(path.resolve(SOURCE_FILE), createFault1(SET1_CASE8C, F1_SINGLE_6P0_FLOAT_MFD));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		// TODO need to be able to specify point source model; check that for actual point source
		// ruptureScaling is ignored
		path = Paths.get(MODEL_DIR, SET1_CASE10, AREA.toString());
		write(path.resolve(SOURCE_FILE), createArea(SET1_CASE10, A1_GR_MFD, AREA_DEPTH_STR));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);

		path = Paths.get(MODEL_DIR, SET1_CASE11, AREA.toString());
		write(path.resolve(SOURCE_FILE), createArea(SET1_CASE11, A1_GR_MFD, AREA_DEPTH_VAR_STR));
		GmmCreator.write(path.resolve(GMM_FILE), GMM_MAP_LIST, GMM_CUTOFFS, null, null);
		
	}

	
	
	private Document createFault1(String testName, IncrementalMfd mfd) {
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(FAULT_SOURCE_SET.toString());
		addAttribute(NAME, testName, root);
		addAttribute(WEIGHT, 1.0, root);
		doc.appendChild(root);

		Element settings = addElement(SETTINGS, root);

		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(RUPTURE_SCALING, PEER, propsElem);

		Element srcElem = addElement(SOURCE, root);
		addAttribute(NAME, "Fault 1", srcElem);
		addMfd(mfd, srcElem);

		Element geom = addElement(GEOMETRY, srcElem);
		addAttribute(DIP, FAULT1_DIP, geom);
		addAttribute(WIDTH, FAULT1_WIDTH, geom);
		addAttribute(RAKE, FAULT1_RAKE, geom);
		addAttribute(DEPTH, FAULT1_ZTOP, geom);
		Element trace = addElement(TRACE, geom);
		trace.setTextContent(FAULT_SOURCE_TRACE.toString());

		return doc;
	}
	
	private Document createFault2(String testName, IncrementalMfd mfd) {
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(FAULT_SOURCE_SET.toString());
		addAttribute(NAME, testName, root);
		addAttribute(WEIGHT, 1.0, root);
		doc.appendChild(root);

		Element settings = addElement(SETTINGS, root);

		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(RUPTURE_SCALING, PEER, propsElem);

		Element srcElem = addElement(SOURCE, root);
		addAttribute(NAME, "Fault 2", srcElem);
		addMfd(mfd, srcElem);

		Element geom = addElement(GEOMETRY, srcElem);
		addAttribute(DIP, FAULT2_DIP, geom);
		addAttribute(WIDTH, FAULT2_WIDTH, geom);
		addAttribute(RAKE, FAULT2_RAKE, geom);
		addAttribute(DEPTH, FAULT2_ZTOP, geom);
		Element trace = addElement(TRACE, geom);
		trace.setTextContent(FAULT_SOURCE_TRACE.toString());

		return doc;
	}
	
	private Document createArea(String testName, IncrementalMfd mfd, String magDepthMap) {
		Document doc = docBuilder.newDocument();
		doc.setXmlStandalone(true);
		Element root = doc.createElement(AREA_SOURCE_SET.toString());
		addAttribute(NAME, testName, root);
		addAttribute(WEIGHT, 1.0, root);
		doc.appendChild(root);

		Element settings = addElement(SETTINGS, root);

		Element propsElem = addElement(SOURCE_PROPERTIES, settings);
		addAttribute(MAG_DEPTH_MAP, magDepthMap, propsElem);
		addAttribute(RUPTURE_SCALING, PEER, propsElem);

		Element srcElem = addElement(SOURCE, root);
		addAttribute(NAME, "Area 1", srcElem);
		addMfd(mfd, srcElem);
		Element geom = addElement(GEOMETRY, srcElem);
		Element border = addElement(BORDER, geom);

		return doc;
	}

	private static void write(Path dest, Document doc) throws IOException, TransformerException {
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer trans = transformerFactory.newTransformer();
		trans.setOutputProperty(OutputKeys.INDENT, "yes");
		trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		trans.setOutputProperty(OutputKeys.STANDALONE, "yes");

		DOMSource source = new DOMSource(doc);
		Files.createDirectories(dest.getParent());
		StreamResult result = new StreamResult(Files.newOutputStream(dest));

		trans.transform(source, result);
	}
	
	private static void addMfd(IncrementalMfd mfd, Element e) {
		Element mfdElem = addElement(INCREMENTAL_MFD, e);
		addAttribute(TYPE, INCR, mfdElem);
		addAttribute(RATES, Parsing.toString(mfd.yValues(), "%.8g"), mfdElem);
		addAttribute(MAGS, Parsing.toString(mfd.xValues(), "%.3f"), mfdElem);
		addAttribute(FLOATS, mfd.floats(), mfdElem);
	}
	
}
